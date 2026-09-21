import os
import sys
import json
import csv
import stat
import shutil
import queue
import threading
import traceback
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

try:
    import pythoncom
    import win32com.client
except Exception:
    pythoncom = None
    win32com = None

APP_NAME = "Word Batch Studio Pro"
APP_VERSION = "1.0"
SUPPORTED_EXTENSIONS = {".docx", ".docm", ".doc", ".dotx", ".dotm", ".rtf"}

WD_NO_PROTECTION = -1
WD_ALLOW_ONLY_REVISIONS = 0
WD_ALLOW_ONLY_COMMENTS = 1
WD_ALLOW_ONLY_FORM_FIELDS = 2
WD_ALLOW_ONLY_READING = 3
WD_FIND_STOP = 0

STORY_BODY = {1}
STORY_NOTES_COMMENTS = {2, 3, 4}
STORY_TEXTBOX = {5}
STORY_HEADER = {6, 7, 10}
STORY_FOOTER = {8, 9, 11}

PROTECTION_OPTIONS = {
    "Keep original protection": None,
    "Leave output unlocked": WD_NO_PROTECTION,
    "Lock output - Tracked changes only": WD_ALLOW_ONLY_REVISIONS,
    "Lock output - Comments only": WD_ALLOW_ONLY_COMMENTS,
    "Lock output - Forms only": WD_ALLOW_ONLY_FORM_FIELDS,
    "Lock output - Read only": WD_ALLOW_ONLY_READING,
}

SCOPE_OPTIONS = [
    "Everywhere",
    "Main body",
    "Headers",
    "Footers",
    "Text boxes",
    "Footnotes / Endnotes / Comments",
]
TYPE_OPTIONS = ["Text", "Image"]


@dataclass
class Rule:
    kind: str
    find: str
    replace: str
    scope: str
    match_case: bool = False
    whole_word: bool = False


def scope_allows_story(scope, story_type):
    if scope == "Everywhere":
        return True
    if scope == "Main body":
        return story_type in STORY_BODY
    if scope == "Headers":
        return story_type in STORY_HEADER
    if scope == "Footers":
        return story_type in STORY_FOOTER
    if scope == "Text boxes":
        return story_type in STORY_TEXTBOX
    if scope == "Footnotes / Endnotes / Comments":
        return story_type in STORY_NOTES_COMMENTS
    return True


def normalize_find_text(value):
    return value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "^p")


def normalize_replacement_text(value):
    return value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r")


class RuleCard:
    def __init__(self, owner, parent, number, initial=None):
        self.owner = owner
        self.frame = ttk.Frame(parent, style="Card.TFrame", padding=(12, 10))
        self.frame.columnconfigure(1, weight=1)
        self.frame.columnconfigure(2, weight=1)

        self.number_var = tk.StringVar(value=f"Rule {number}")
        ttk.Label(self.frame, textvariable=self.number_var, style="CardTitle.TLabel").grid(
            row=0, column=0, sticky="w", padx=(0, 8)
        )

        self.kind = tk.StringVar(value="Text")
        self.kind_combo = ttk.Combobox(
            self.frame, textvariable=self.kind, values=TYPE_OPTIONS, state="readonly", width=11
        )
        self.kind_combo.grid(row=0, column=1, sticky="w", padx=(0, 8))
        self.kind_combo.bind("<<ComboboxSelected>>", lambda _e: self.refresh_kind())

        self.scope = tk.StringVar(value="Everywhere")
        ttk.Combobox(
            self.frame, textvariable=self.scope, values=SCOPE_OPTIONS, state="readonly", width=30
        ).grid(row=0, column=2, sticky="w", padx=(0, 8))

        self.case = tk.BooleanVar(value=False)
        self.whole = tk.BooleanVar(value=False)
        self.case_check = ttk.Checkbutton(self.frame, text="Match case", variable=self.case)
        self.whole_check = ttk.Checkbutton(self.frame, text="Whole word", variable=self.whole)
        self.case_check.grid(row=0, column=3, sticky="w", padx=4)
        self.whole_check.grid(row=0, column=4, sticky="w", padx=4)

        ttk.Button(
            self.frame, text="Remove", style="Danger.TButton",
            command=lambda: owner.remove_rule(self)
        ).grid(row=0, column=5, sticky="e", padx=(10, 0))

        ttk.Label(self.frame, text="Find / Target", style="Muted.TLabel").grid(
            row=1, column=1, sticky="w", pady=(9, 2)
        )
        ttk.Label(self.frame, text="Replace with", style="Muted.TLabel").grid(
            row=1, column=2, sticky="w", pady=(9, 2)
        )

        self.find_text = tk.Text(
            self.frame, height=3, wrap="word", relief="solid", borderwidth=1,
            undo=True, font=("Segoe UI", 9)
        )
        self.replace_text = tk.Text(
            self.frame, height=3, wrap="word", relief="solid", borderwidth=1,
            undo=True, font=("Segoe UI", 9)
        )
        self.find_text.grid(row=2, column=1, sticky="nsew", padx=(0, 8))
        self.replace_text.grid(row=2, column=2, columnspan=3, sticky="nsew", padx=(0, 8))

        self.browse_btn = ttk.Button(self.frame, text="Browse image...", command=self.browse_image)
        self.browse_btn.grid(row=2, column=5, sticky="n")

        self.hint = ttk.Label(self.frame, style="Hint.TLabel")
        self.hint.grid(row=3, column=1, columnspan=5, sticky="w", pady=(5, 0))

        if initial:
            self.kind.set(initial.get("kind", "Text"))
            self.scope.set(initial.get("scope", "Everywhere"))
            self.case.set(bool(initial.get("match_case", False)))
            self.whole.set(bool(initial.get("whole_word", False)))
            self.find_text.insert("1.0", initial.get("find", ""))
            self.replace_text.insert("1.0", initial.get("replace", ""))

        self.refresh_kind()

    def browse_image(self):
        filename = filedialog.askopenfilename(
            title="Select replacement image",
            filetypes=[
                ("Image files", "*.png *.jpg *.jpeg *.bmp *.gif *.tif *.tiff"),
                ("All files", "*.*"),
            ],
        )
        if filename:
            self.replace_text.delete("1.0", "end")
            self.replace_text.insert("1.0", filename)

    def refresh_kind(self):
        is_image = self.kind.get() == "Image"
        self.browse_btn.configure(state="normal" if is_image else "disabled")
        if is_image:
            self.case_check.state(["disabled"])
            self.whole_check.state(["disabled"])
            self.hint.configure(
                text="Image target: use #1, #2, etc. or match its Alt Text / Title / Shape Name."
            )
        else:
            self.case_check.state(["!disabled"])
            self.whole_check.state(["!disabled"])
            self.hint.configure(
                text="Supports words, sentences and multi-line paragraphs. Paste the full paragraph if needed."
            )

    def get_rule(self):
        return Rule(
            kind=self.kind.get(),
            find=self.find_text.get("1.0", "end-1c"),
            replace=self.replace_text.get("1.0", "end-1c"),
            scope=self.scope.get(),
            match_case=self.case.get(),
            whole_word=self.whole.get(),
        )

    def destroy(self):
        self.frame.destroy()


class WordBatchStudio(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(f"{APP_NAME} {APP_VERSION}")
        self.geometry("1280x820")
        self.minsize(1030, 680)
        self.configure(bg="#f3f5f8")

        self.rule_cards = []
        self.ui_queue = queue.Queue()
        self.cancel_event = threading.Event()
        self.worker = None

        self._style()
        self._build_ui()
        self.after(100, self._drain_queue)

    def _style(self):
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass
        style.configure(".", font=("Segoe UI", 9))
        style.configure("Top.TFrame", background="#17243a")
        style.configure(
            "TopTitle.TLabel", background="#17243a", foreground="white",
            font=("Segoe UI Semibold", 18)
        )
        style.configure(
            "TopSub.TLabel", background="#17243a", foreground="#cbd5e1",
            font=("Segoe UI", 9)
        )
        style.configure("Panel.TFrame", background="white")
        style.configure("Card.TFrame", background="white", relief="solid", borderwidth=1)
        style.configure(
            "CardTitle.TLabel", background="white", foreground="#0f172a",
            font=("Segoe UI Semibold", 10)
        )
        style.configure("Muted.TLabel", background="white", foreground="#475569")
        style.configure(
            "Hint.TLabel", background="white", foreground="#64748b",
            font=("Segoe UI", 8)
        )
        style.configure(
            "Section.TLabel", background="white", foreground="#0f172a",
            font=("Segoe UI Semibold", 11)
        )
        style.configure("Primary.TButton", font=("Segoe UI Semibold", 10), padding=(16, 8))
        style.configure("Danger.TButton", padding=(8, 4))
        style.configure("Status.TLabel", background="#eef2f7", foreground="#334155")

    def _build_ui(self):
        top = ttk.Frame(self, style="Top.TFrame", padding=(22, 15))
        top.pack(fill="x")
        ttk.Label(top, text="Word Batch Studio Pro", style="TopTitle.TLabel").pack(anchor="w")
        ttk.Label(
            top,
            text="Portable Word batch editor • originals untouched • edited copies saved separately",
            style="TopSub.TLabel",
        ).pack(anchor="w", pady=(2, 0))

        body = ttk.Frame(self, padding=14)
        body.pack(fill="both", expand=True)
        body.columnconfigure(0, weight=1)
        body.rowconfigure(2, weight=1)

        files_panel = ttk.Frame(body, style="Panel.TFrame", padding=12)
        files_panel.grid(row=0, column=0, sticky="ew", pady=(0, 10))
        files_panel.columnconfigure(1, weight=1)

        ttk.Label(files_panel, text="Batch folders", style="Section.TLabel").grid(
            row=0, column=0, columnspan=4, sticky="w"
        )

        ttk.Label(files_panel, text="Source folder", style="Muted.TLabel").grid(
            row=1, column=0, sticky="w", pady=(10, 4)
        )
        self.source_var = tk.StringVar()
        ttk.Entry(files_panel, textvariable=self.source_var).grid(
            row=1, column=1, sticky="ew", padx=(8, 6), pady=(10, 4)
        )
        ttk.Button(files_panel, text="Browse...", command=self.choose_source).grid(
            row=1, column=2, pady=(10, 4)
        )

        ttk.Label(files_panel, text="Output folder", style="Muted.TLabel").grid(
            row=2, column=0, sticky="w", pady=4
        )
        self.output_var = tk.StringVar()
        ttk.Entry(files_panel, textvariable=self.output_var).grid(
            row=2, column=1, sticky="ew", padx=(8, 6), pady=4
        )
        ttk.Button(files_panel, text="Browse...", command=self.choose_output).grid(
            row=2, column=2, pady=4
        )

        self.recursive_var = tk.BooleanVar(value=True)
        self.update_fields_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            files_panel, text="Include subfolders", variable=self.recursive_var
        ).grid(row=3, column=1, sticky="w", padx=(8, 0), pady=(5, 0))
        ttk.Checkbutton(
            files_panel, text="Update Word fields after changes",
            variable=self.update_fields_var
        ).grid(row=3, column=2, columnspan=2, sticky="w", padx=(8, 0), pady=(5, 0))

        protection = ttk.Frame(body, style="Panel.TFrame", padding=12)
        protection.grid(row=1, column=0, sticky="ew", pady=(0, 10))
        protection.columnconfigure(5, weight=1)

        ttk.Label(
            protection, text="Protection & locked content", style="Section.TLabel"
        ).grid(row=0, column=0, columnspan=6, sticky="w")

        ttk.Label(
            protection, text="Password (only if your document uses one)", style="Muted.TLabel"
        ).grid(row=1, column=0, sticky="w", pady=(10, 4))
        self.password_var = tk.StringVar()
        ttk.Entry(
            protection, textvariable=self.password_var, show="•", width=28
        ).grid(row=1, column=1, sticky="w", padx=(8, 16), pady=(10, 4))

        ttk.Label(
            protection, text="Output protection", style="Muted.TLabel"
        ).grid(row=1, column=2, sticky="w", pady=(10, 4))
        self.protection_var = tk.StringVar(value="Keep original protection")
        ttk.Combobox(
            protection, textvariable=self.protection_var,
            values=list(PROTECTION_OPTIONS.keys()), state="readonly", width=34
        ).grid(row=1, column=3, sticky="w", padx=(8, 16), pady=(10, 4))

        self.unlock_cc_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            protection, text="Unlock content controls in edited copy",
            variable=self.unlock_cc_var
        ).grid(row=1, column=4, columnspan=2, sticky="w", pady=(10, 4))

        ttk.Label(
            protection,
            text="Protected files are edited only when Word accepts the supplied password. Unknown passwords are not bypassed.",
            style="Hint.TLabel",
        ).grid(row=2, column=0, columnspan=6, sticky="w", pady=(4, 0))

        rules_outer = ttk.Frame(body, style="Panel.TFrame", padding=(12, 10))
        rules_outer.grid(row=2, column=0, sticky="nsew")
        rules_outer.columnconfigure(0, weight=1)
        rules_outer.rowconfigure(1, weight=1)

        toolbar = ttk.Frame(rules_outer, style="Panel.TFrame")
        toolbar.grid(row=0, column=0, sticky="ew", pady=(0, 8))
        ttk.Label(toolbar, text="Find & Replace rules", style="Section.TLabel").pack(side="left")
        ttk.Button(toolbar, text="+ Add rule", command=self.add_rule).pack(side="left", padx=(14, 6))
        ttk.Button(toolbar, text="Add image rule", command=lambda: self.add_rule({"kind": "Image"})).pack(side="left", padx=6)
        ttk.Button(toolbar, text="Clear rules", command=self.clear_rules).pack(side="left", padx=6)
        ttk.Button(toolbar, text="Save rules", command=self.save_rules).pack(side="left", padx=6)
        ttk.Button(toolbar, text="Load rules", command=self.load_rules).pack(side="left", padx=6)

        canvas_frame = ttk.Frame(rules_outer, style="Panel.TFrame")
        canvas_frame.grid(row=1, column=0, sticky="nsew")
        canvas_frame.rowconfigure(0, weight=1)
        canvas_frame.columnconfigure(0, weight=1)

        self.canvas = tk.Canvas(canvas_frame, highlightthickness=0, bg="#f8fafc")
        scroll = ttk.Scrollbar(canvas_frame, orient="vertical", command=self.canvas.yview)
        self.rules_container = ttk.Frame(self.canvas, padding=4)
        self.rules_window = self.canvas.create_window(
            (0, 0), window=self.rules_container, anchor="nw"
        )
        self.canvas.configure(yscrollcommand=scroll.set)
        self.canvas.grid(row=0, column=0, sticky="nsew")
        scroll.grid(row=0, column=1, sticky="ns")
        self.rules_container.bind("<Configure>", self._update_scroll)
        self.canvas.bind("<Configure>", self._resize_rules_window)
        self.canvas.bind_all("<MouseWheel>", self._on_mousewheel)

        self.add_rule()
        self.add_rule()
        self.add_rule()

        bottom = ttk.Frame(body, padding=(0, 10, 0, 0))
        bottom.grid(row=3, column=0, sticky="ew")
        bottom.columnconfigure(1, weight=1)

        self.run_btn = ttk.Button(
            bottom, text="RUN BATCH EDIT", style="Primary.TButton",
            command=self.start_processing
        )
        self.run_btn.grid(row=0, column=0, sticky="w")

        self.stop_btn = ttk.Button(
            bottom, text="Stop", command=self.stop_processing, state="disabled"
        )
        self.stop_btn.grid(row=0, column=1, sticky="w", padx=(8, 0))

        self.progress = ttk.Progressbar(bottom, mode="determinate", length=360)
        self.progress.grid(row=0, column=2, sticky="e", padx=(10, 0))

        self.status_var = tk.StringVar(value="Ready")
        ttk.Label(
            bottom, textvariable=self.status_var, style="Status.TLabel", padding=(8, 4)
        ).grid(row=1, column=0, columnspan=3, sticky="ew", pady=(8, 0))

        self.log = tk.Text(
            body, height=8, state="disabled", wrap="word", font=("Consolas", 8),
            bg="#0f172a", fg="#e2e8f0", insertbackground="white"
        )
        self.log.grid(row=4, column=0, sticky="ew", pady=(10, 0))

    def _update_scroll(self, _event=None):
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _resize_rules_window(self, event):
        self.canvas.itemconfigure(self.rules_window, width=event.width)

    def _on_mousewheel(self, event):
        if self.canvas.winfo_exists():
            self.canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

    def choose_source(self):
        folder = filedialog.askdirectory(title="Choose folder containing Word documents")
        if folder:
            self.source_var.set(folder)
            if not self.output_var.get().strip():
                self.output_var.set(os.path.join(folder, "Edited Documents"))

    def choose_output(self):
        folder = filedialog.askdirectory(title="Choose output folder")
        if folder:
            self.output_var.set(folder)

    def add_rule(self, initial=None):
        card = RuleCard(self, self.rules_container, len(self.rule_cards) + 1, initial)
        self.rule_cards.append(card)
        card.frame.pack(fill="x", pady=5)
        self._renumber_rules()

    def remove_rule(self, card):
        if len(self.rule_cards) <= 1:
            messagebox.showinfo(APP_NAME, "Keep at least one rule. You can clear its text.")
            return
        self.rule_cards.remove(card)
        card.destroy()
        self._renumber_rules()

    def clear_rules(self):
        for card in list(self.rule_cards):
            card.destroy()
        self.rule_cards = []
        self.add_rule()

    def _renumber_rules(self):
        for idx, card in enumerate(self.rule_cards, 1):
            card.number_var.set(f"Rule {idx}")

    def collect_rules(self):
        rules = []
        for card in self.rule_cards:
            rule = card.get_rule()
            if rule.find.strip():
                rules.append(rule)
        return rules

    def save_rules(self):
        rules = [r.__dict__ for r in self.collect_rules()]
        if not rules:
            messagebox.showwarning(APP_NAME, "There are no completed rules to save.")
            return
        path = filedialog.asksaveasfilename(
            title="Save rule set", defaultextension=".json",
            filetypes=[("Word Batch Studio rules", "*.json"), ("JSON", "*.json")]
        )
        if path:
            with open(path, "w", encoding="utf-8") as f:
                json.dump({"version": 1, "rules": rules}, f, ensure_ascii=False, indent=2)

    def load_rules(self):
        path = filedialog.askopenfilename(
            title="Load rule set",
            filetypes=[("Word Batch Studio rules", "*.json"), ("JSON", "*.json"), ("All files", "*.*")]
        )
        if not path:
            return
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            rows = data.get("rules", data if isinstance(data, list) else [])
            if not rows:
                raise ValueError("No rules found in the selected file.")
            for card in list(self.rule_cards):
                card.destroy()
            self.rule_cards = []
            for item in rows:
                self.add_rule(item)
        except Exception as exc:
            messagebox.showerror(APP_NAME, f"Could not load rule file.\n\n{exc}")

    def log_line(self, text):
        self.log.configure(state="normal")
        self.log.insert("end", text + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    def start_processing(self):
        if self.worker and self.worker.is_alive():
            return

        source = self.source_var.get().strip()
        output = self.output_var.get().strip()
        rules = self.collect_rules()

        if not source or not os.path.isdir(source):
            messagebox.showerror(APP_NAME, "Choose a valid source folder.")
            return
        if not output:
            output = os.path.join(source, "Edited Documents")
            self.output_var.set(output)
        if os.path.abspath(source) == os.path.abspath(output):
            messagebox.showerror(APP_NAME, "Output folder must be different from the source folder.")
            return
        if not rules:
            messagebox.showerror(APP_NAME, "Add at least one Find / Replace rule.")
            return
        if pythoncom is None or win32com is None:
            messagebox.showerror(
                APP_NAME, "Microsoft Word automation components are not available in this build."
            )
            return

        missing_images = [
            r.replace for r in rules
            if r.kind == "Image" and not os.path.isfile(r.replace)
        ]
        if missing_images:
            messagebox.showerror(
                APP_NAME, "Replacement image not found:\n\n" + "\n".join(missing_images[:5])
            )
            return

        config = {
            "source": source,
            "output": output,
            "rules": rules,
            "recursive": self.recursive_var.get(),
            "update_fields": self.update_fields_var.get(),
            "password": self.password_var.get(),
            "protection_action": self.protection_var.get(),
            "unlock_content_controls": self.unlock_cc_var.get(),
        }

        self.cancel_event.clear()
        self.run_btn.configure(state="disabled")
        self.stop_btn.configure(state="normal")
        self.progress["value"] = 0
        self.status_var.set("Starting Microsoft Word...")
        self.log.configure(state="normal")
        self.log.delete("1.0", "end")
        self.log.configure(state="disabled")

        self.worker = threading.Thread(
            target=self._worker_main, args=(config,), daemon=True
        )
        self.worker.start()

    def stop_processing(self):
        self.cancel_event.set()
        self.status_var.set("Stopping after the current document...")

    def _drain_queue(self):
        try:
            while True:
                item = self.ui_queue.get_nowait()
                kind = item[0]
                if kind == "log":
                    self.log_line(item[1])
                elif kind == "progress":
                    done, total, name = item[1:]
                    self.progress["maximum"] = max(total, 1)
                    self.progress["value"] = done
                    self.status_var.set(f"{done}/{total} • {name}")
                elif kind == "done":
                    summary = item[1]
                    self.run_btn.configure(state="normal")
                    self.stop_btn.configure(state="disabled")
                    self.status_var.set(summary)
                    if item[2]:
                        messagebox.showinfo(APP_NAME, summary + "\n\n" + item[2])
                elif kind == "error":
                    self.run_btn.configure(state="normal")
                    self.stop_btn.configure(state="disabled")
                    self.status_var.set("Failed")
                    messagebox.showerror(APP_NAME, item[1])
        except queue.Empty:
            pass
        self.after(100, self._drain_queue)

    def _discover_files(self, source, output, recursive):
        source_p = Path(source)
        output_p = Path(output)
        iterator = source_p.rglob("*") if recursive else source_p.glob("*")
        files = []
        for p in iterator:
            if not p.is_file() or p.name.startswith("~$"):
                continue
            if p.suffix.lower() not in SUPPORTED_EXTENSIONS:
                continue
            try:
                p_abs = p.resolve()
                out_abs = output_p.resolve()
                if out_abs in p_abs.parents:
                    continue
            except Exception:
                pass
            files.append(p)
        return sorted(files, key=lambda x: str(x).lower())

    def _worker_main(self, config):
        pythoncom.CoInitialize()
        word = None
        report_rows = []
        try:
            files = self._discover_files(
                config["source"], config["output"], config["recursive"]
            )
            if not files:
                self.ui_queue.put((
                    "error", "No supported Word files were found in the selected folder."
                ))
                return

            os.makedirs(config["output"], exist_ok=True)
            self.ui_queue.put(("log", f"Found {len(files)} Word document(s)."))

            word = win32com.client.DispatchEx("Word.Application")
            word.Visible = False
            word.DisplayAlerts = 0
            try:
                word.AutomationSecurity = 3
            except Exception:
                pass

            success = 0
            failed = 0
            skipped = 0

            for idx, src in enumerate(files, 1):
                if self.cancel_event.is_set():
                    self.ui_queue.put(("log", "Batch stopped by user."))
                    break

                rel = src.relative_to(Path(config["source"]))
                dest = Path(config["output"]) / rel
                dest.parent.mkdir(parents=True, exist_ok=True)

                self.ui_queue.put(("progress", idx - 1, len(files), src.name))
                self.ui_queue.put(("log", f"[{idx}/{len(files)}] {rel}"))

                row = {
                    "file": str(rel),
                    "status": "",
                    "text_replacements": 0,
                    "image_replacements": 0,
                    "details": "",
                }

                try:
                    shutil.copy2(src, dest)
                    try:
                        os.chmod(dest, stat.S_IREAD | stat.S_IWRITE)
                    except Exception:
                        pass

                    result = self._process_document(word, str(dest), config)
                    row.update(result)
                    if result["status"] == "OK":
                        success += 1
                        self.ui_queue.put((
                            "log",
                            f"    OK • text {result['text_replacements']} • images {result['image_replacements']}"
                        ))
                    else:
                        skipped += 1
                        self.ui_queue.put((
                            "log", f"    {result['status']} • {result['details']}"
                        ))
                except Exception as exc:
                    failed += 1
                    row["status"] = "FAILED"
                    row["details"] = str(exc)
                    self.ui_queue.put(("log", f"    FAILED • {exc}"))

                report_rows.append(row)
                self.ui_queue.put(("progress", idx, len(files), src.name))

            report_path = self._write_report(config["output"], report_rows)
            summary = f"Completed • {success} edited • {failed} failed • {skipped} skipped"
            self.ui_queue.put((
                "done", summary,
                f"Edited documents:\n{config['output']}\n\nReport:\n{report_path}"
            ))
        except Exception as exc:
            self.ui_queue.put(("log", traceback.format_exc()))
            self.ui_queue.put(("error", f"Batch processing failed.\n\n{exc}"))
        finally:
            if word is not None:
                try:
                    word.Quit()
                except Exception:
                    pass
            pythoncom.CoUninitialize()

    def _write_report(self, output, rows):
        path = Path(output) / (
            f"Batch_Edit_Report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
        )
        with open(path, "w", newline="", encoding="utf-8-sig") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=[
                    "file", "status", "text_replacements",
                    "image_replacements", "details"
                ],
            )
            writer.writeheader()
            writer.writerows(rows)
        return str(path)

    def _process_document(self, word, path, config):
        doc = None
        original_protection = WD_NO_PROTECTION
        original_track = False
        text_count = 0
        image_count = 0

        try:
            doc = word.Documents.Open(
                FileName=os.path.abspath(path),
                ReadOnly=False,
                AddToRecentFiles=False,
                Visible=False,
                ConfirmConversions=False,
            )

            try:
                original_protection = int(doc.ProtectionType)
            except Exception:
                original_protection = WD_NO_PROTECTION

            password = config["password"]
            if original_protection != WD_NO_PROTECTION:
                try:
                    doc.Unprotect(Password=password)
                except Exception:
                    return {
                        "status": "SKIPPED - PROTECTED",
                        "text_replacements": 0,
                        "image_replacements": 0,
                        "details": "Protection could not be removed. Supply the correct password.",
                    }

            if config["unlock_content_controls"]:
                self._unlock_content_controls(doc)

            try:
                original_track = bool(doc.TrackRevisions)
                doc.TrackRevisions = False
            except Exception:
                original_track = False

            for rule in config["rules"]:
                if self.cancel_event.is_set():
                    break
                if rule.kind == "Text":
                    text_count += self._apply_text_rule(doc, rule)
                else:
                    image_count += self._apply_image_rule(doc, rule)

            if config["update_fields"]:
                self._update_all_fields(doc)

            action = config["protection_action"]
            desired = PROTECTION_OPTIONS.get(action)

            try:
                if int(doc.ProtectionType) != WD_NO_PROTECTION:
                    doc.Unprotect(Password=password)
            except Exception:
                pass

            if action == "Keep original protection":
                if original_protection != WD_NO_PROTECTION:
                    doc.Protect(
                        Type=original_protection, NoReset=True, Password=password
                    )
            elif desired == WD_NO_PROTECTION:
                pass
            elif desired is not None:
                doc.Protect(Type=desired, NoReset=True, Password=password)

            try:
                doc.TrackRevisions = original_track
            except Exception:
                pass

            doc.Save()
            return {
                "status": "OK",
                "text_replacements": text_count,
                "image_replacements": image_count,
                "details": "",
            }
        finally:
            if doc is not None:
                try:
                    doc.Close(SaveChanges=False)
                except Exception:
                    pass

    def _unlock_content_controls(self, doc):
        def unlock_collection(collection):
            try:
                count = int(collection.Count)
            except Exception:
                return
            for i in range(1, count + 1):
                try:
                    cc = collection.Item(i)
                    try:
                        cc.LockContents = False
                    except Exception:
                        pass
                    try:
                        cc.LockContentControl = False
                    except Exception:
                        pass
                except Exception:
                    pass

        try:
            unlock_collection(doc.ContentControls)
        except Exception:
            pass
        for _story_type, rng in self._iter_story_ranges(doc):
            try:
                unlock_collection(rng.ContentControls)
            except Exception:
                pass

    def _iter_story_ranges(self, doc):
        seen = set()
        for story_type in range(1, 18):
            try:
                rng = doc.StoryRanges.Item(story_type)
            except Exception:
                continue
            safety = 0
            while rng is not None and safety < 200:
                safety += 1
                try:
                    key = (story_type, int(rng.Start), int(rng.End))
                except Exception:
                    key = (story_type, safety)
                if key in seen:
                    break
                seen.add(key)
                yield story_type, rng
                try:
                    rng = rng.NextStoryRange
                except Exception:
                    break

    def _apply_text_rule(self, doc, rule):
        total = 0
        for story_type, rng in self._iter_story_ranges(doc):
            if scope_allows_story(rule.scope, story_type):
                total += self._replace_in_range(rng, rule)
        return total

    def _replace_in_range(self, base_range, rule):
        find_text = normalize_find_text(rule.find)
        replacement = normalize_replacement_text(rule.replace)
        if not find_text:
            return 0

        count = 0
        work = base_range.Duplicate
        guard = 0

        while guard < 100000:
            guard += 1
            start_bound = int(work.Start)
            end_bound = int(work.End)
            if start_bound >= end_bound:
                break

            finder = work.Find
            finder.ClearFormatting()
            try:
                finder.Replacement.ClearFormatting()
            except Exception:
                pass

            found = finder.Execute(
                FindText=find_text,
                MatchCase=rule.match_case,
                MatchWholeWord=rule.whole_word,
                MatchWildcards=False,
                Forward=True,
                Wrap=WD_FIND_STOP,
                Format=False,
            )
            if not found:
                break

            match_start = int(work.Start)
            match_end = int(work.End)
            work.Text = replacement
            count += 1

            next_start = int(work.End)
            try:
                current_end = int(base_range.End)
            except Exception:
                current_end = max(next_start, end_bound)

            if next_start <= match_start and match_end <= match_start:
                next_start = match_start + 1
            if next_start >= current_end:
                break
            work.SetRange(next_start, current_end)

        return count

    def _target_matches(self, target, values, index):
        t = target.strip()
        if not t:
            return False
        if t.startswith("#") and t[1:].isdigit():
            return index == int(t[1:])
        t_low = t.casefold()
        for value in values:
            if value and t_low in str(value).casefold():
                return True
        return False

    def _apply_image_rule(self, doc, rule):
        path = os.path.abspath(rule.replace)
        count = 0
        image_index = 0

        for story_type, rng in self._iter_story_ranges(doc):
            if not scope_allows_story(rule.scope, story_type):
                continue

            try:
                inline_count = int(rng.InlineShapes.Count)
            except Exception:
                inline_count = 0

            for i in range(1, inline_count + 1):
                try:
                    shape = rng.InlineShapes.Item(i)
                    image_index += 1
                    values = [
                        getattr(shape, "AlternativeText", ""),
                        getattr(shape, "Title", ""),
                    ]
                    if not self._target_matches(
                        rule.find, values, image_index
                    ):
                        continue

                    width = float(shape.Width)
                    height = float(shape.Height)
                    insert_range = shape.Range.Duplicate
                    insert_range.Collapse(1)
                    shape.Delete()

                    new_shape = rng.InlineShapes.AddPicture(
                        FileName=path,
                        LinkToFile=False,
                        SaveWithDocument=True,
                        Range=insert_range,
                    )
                    try:
                        new_shape.LockAspectRatio = False
                        new_shape.Width = width
                        new_shape.Height = height
                    except Exception:
                        pass
                    count += 1
                except Exception:
                    pass

            try:
                shape_count = int(rng.ShapeRange.Count)
            except Exception:
                shape_count = 0

            for i in range(1, shape_count + 1):
                try:
                    sh = rng.ShapeRange.Item(i)
                    image_index += 1
                    values = [
                        getattr(sh, "AlternativeText", ""),
                        getattr(sh, "Title", ""),
                        getattr(sh, "Name", ""),
                    ]
                    if not self._target_matches(
                        rule.find, values, image_index
                    ):
                        continue

                    left = float(sh.Left)
                    top = float(sh.Top)
                    width = float(sh.Width)
                    height = float(sh.Height)
                    anchor = sh.Anchor.Duplicate
                    try:
                        wrap_type = sh.WrapFormat.Type
                    except Exception:
                        wrap_type = None

                    sh.Delete()
                    new_sh = doc.Shapes.AddPicture(
                        FileName=path,
                        LinkToFile=False,
                        SaveWithDocument=True,
                        Left=left,
                        Top=top,
                        Width=width,
                        Height=height,
                        Anchor=anchor,
                    )
                    if wrap_type is not None:
                        try:
                            new_sh.WrapFormat.Type = wrap_type
                        except Exception:
                            pass
                    count += 1
                except Exception:
                    pass

        return count

    def _update_all_fields(self, doc):
        for _story_type, rng in self._iter_story_ranges(doc):
            try:
                rng.Fields.Update()
            except Exception:
                pass


if __name__ == "__main__":
    WordBatchStudio().mainloop()

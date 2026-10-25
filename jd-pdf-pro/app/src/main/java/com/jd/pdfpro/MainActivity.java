package com.jd.pdfpro;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int PICK_PDF=101, PICK_MERGE=102, PICK_IMAGES=103, SAVE_PDF=104;
    private static final int NAVY=Color.rgb(12,34,60), BLUE=Color.rgb(22,96,204), INK=Color.rgb(28,42,60);
    private static final String PREFS="jd_pdf_pro";
    private LinearLayout body, bottom;
    private TextView heading, detail, empty, pageLabel;
    private ImageView pageImage;
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private Uri documentUri;
    private String fileName="document.pdf", exportKind="", stampText="";
    private int pageIndex=0, exportPageIndex=0;
    private float zoom=1f;
    private ArrayList<Uri> inputUris=new ArrayList<>();
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        PDFBoxResourceLoader.init(getApplicationContext());
        buildInterface();
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData()!=null)
            openPdf(getIntent().getData());
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable shape(int color, int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String s, int sp, int color, boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(sp);
        if(bold) t.setTypeface(null,1); return t;
    }
    private Button button(String title, boolean accent, View.OnClickListener click){
        Button b=new Button(this); b.setAllCaps(false); b.setText(title); b.setTextSize(13); b.setMinHeight(0);
        b.setMinimumHeight(0); b.setPadding(dp(12),dp(8),dp(12),dp(8));
        b.setTextColor(accent ? Color.WHITE : INK);
        b.setBackground(shape(accent ? BLUE : 0xffe8edf4,12)); b.setOnClickListener(click); return b;
    }
    private void addButton(LinearLayout row,String label,boolean accent,View.OnClickListener click){
        Button b=button(label,accent,click);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(43)); p.setMargins(0,0,dp(7),0); row.addView(b,p);
    }
    private void buildInterface(){
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(0xfff5f7fb); setContentView(body);
        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(NAVY); top.setPadding(dp(18),dp(17),dp(18),dp(15));
        heading=text("JD PDF PRO",23,Color.WHITE,true); top.addView(heading);
        detail=text("Your offline document workspace",12,0xffbbd1eb,false);
        LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2); dp1.topMargin=dp(3); top.addView(detail,dp1);
        HorizontalScrollView bar=new HorizontalScrollView(this); bar.setHorizontalScrollBarEnabled(false);
        LinearLayout actions=new LinearLayout(this); actions.setPadding(0,dp(15),0,0);
        addButton(actions,"Open PDF",true,v->pickPdf());
        addButton(actions,"Search",false,v->searchText());
        addButton(actions,"Tools",false,v->showTools());
        addButton(actions,"Recent",false,v->showRecent());
        bar.addView(actions); top.addView(bar); body.addView(top);

        FrameLayout viewer=new FrameLayout(this);
        viewer.setBackgroundColor(0xffe6ecf3);
        HorizontalScrollView horizontal=new HorizontalScrollView(this);
        horizontal.setFillViewport(true);
        ScrollView vertical=new ScrollView(this); vertical.setFillViewport(true);
        pageImage=new ImageView(this); pageImage.setBackgroundColor(Color.WHITE);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        vertical.addView(pageImage,new ScrollView.LayoutParams(-2,-2));
        horizontal.addView(vertical,new HorizontalScrollView.LayoutParams(-2,-1));
        viewer.addView(horizontal,new FrameLayout.LayoutParams(-1,-1));
        empty=text("JD PDF Pro\n\nOpen a PDF to begin\n\nRead · Search · Merge · Rotate · Export",20,0xff62758b,true);
        empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20),0,dp(20),0);
        viewer.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        body.addView(viewer,new LinearLayout.LayoutParams(-1,0,1));

        bottom=new LinearLayout(this); bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(8),dp(8),dp(8),dp(9)); bottom.setBackgroundColor(Color.WHITE);
        addButton(bottom,"‹",false,v->go(-1));
        pageLabel=text("0 / 0",13,INK,true); pageLabel.setGravity(Gravity.CENTER);
        bottom.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));
        addButton(bottom,"›",false,v->go(1));
        addButton(bottom,"−",false,v->{zoom=Math.max(.6f,zoom-.25f);renderPage();});
        addButton(bottom,"+",false,v->{zoom=Math.min(3f,zoom+.25f);renderPage();});
        body.addView(bottom);
    }
    private void toast(String msg){ Toast.makeText(this,msg,Toast.LENGTH_LONG).show(); }
    private void alert(String title,String message){
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();
    }
    private void pickPdf(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_PDF);
    }
    private void pickMerge(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/pdf"); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,PICK_MERGE);
    }
    private void pickImages(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,PICK_IMAGES);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK || data==null) return;
        if(req==PICK_PDF){
            Uri uri=data.getData(); if(uri==null)return;
            try{ getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception ignored){}
            openPdf(uri); return;
        }
        if(req==PICK_MERGE || req==PICK_IMAGES){
            inputUris.clear();
            if(data.getClipData()!=null)for(int j=0;j<data.getClipData().getItemCount();j++)
                inputUris.add(data.getClipData().getItemAt(j).getUri());
            else if(data.getData()!=null)inputUris.add(data.getData());
            if(req==PICK_MERGE && inputUris.size()<2){ alert("Select two or more PDFs","Use the multi-select option to choose at least two PDF files."); return; }
            if(inputUris.isEmpty()) return;
            requestSave(req==PICK_MERGE ? "merge" : "images",req==PICK_MERGE ? "JD-Merged.pdf" : "JD-Images.pdf");
            return;
        }
        if(req==SAVE_PDF && data.getData()!=null) executeExport(data.getData());
    }
    private void openPdf(Uri uri){
        closeDocument(); try {
            descriptor=getContentResolver().openFileDescriptor(uri,"r");
            if(descriptor==null)throw new IOException("Cannot open this file");
            renderer=new PdfRenderer(descriptor);
            if(renderer.getPageCount()<1)throw new IOException("PDF contains no pages");
            documentUri=uri; fileName=getName(uri); pageIndex=0; zoom=1f;
            detail.setText(fileName+"  ·  "+renderer.getPageCount()+" pages");
            empty.setVisibility(View.GONE); renderPage(); saveRecent(uri);
        } catch(Exception e){closeDocument(); alert("PDF could not be opened",shortError(e));}
    }
    private void closeDocument(){
        if(renderer!=null)try{renderer.close();}catch(Exception ignored){}
        renderer=null; if(descriptor!=null)try{descriptor.close();}catch(Exception ignored){}
        descriptor=null; documentUri=null;
        if(pageImage!=null)pageImage.setImageDrawable(null);
    }
    private String getName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return "document.pdf";
    }
    private void go(int delta){
        if(renderer==null)return;
        pageIndex=Math.max(0,Math.min(renderer.getPageCount()-1,pageIndex+delta));
        renderPage();
    }
    private void renderPage(){
        if(renderer==null)return;
        PdfRenderer.Page p=null;
        try{
            p=renderer.openPage(pageIndex);
            int viewWidth=Math.max(dp(280),getResources().getDisplayMetrics().widthPixels-dp(22));
            int width=Math.min(3000,Math.round(viewWidth*zoom));
            float ratio=(float)p.getHeight()/p.getWidth();
            int height=Math.max(1,Math.min(4200,Math.round(width*ratio)));
            Bitmap bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pageImage.setImageBitmap(bmp);
            pageImage.setLayoutParams(new ScrollView.LayoutParams(width,height));
            pageLabel.setText((pageIndex+1)+" / "+renderer.getPageCount());
        }catch(Exception e){alert("Rendering error",shortError(e));}
        finally{ if(p!=null)p.close(); }
    }
    private void saveRecent(Uri u){
        android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        String existing=p.getString("recent","");
        ArrayList<String> all=new ArrayList<>();all.add(u.toString());
        for(String s:existing.split("\n")) if(!s.isEmpty()&&!s.equals(u.toString())&&all.size()<10) all.add(s);
        p.edit().putString("recent",android.text.TextUtils.join("\n",all)).apply();
    }
    private void showRecent(){
        String r=getSharedPreferences(PREFS,MODE_PRIVATE).getString("recent","");
        if(r.isEmpty()){toast("No recent documents");return;}
        String[] uris=r.split("\n"); String[] names=new String[uris.length];
        for(int j=0;j<uris.length;j++)names[j]=getName(Uri.parse(uris[j]));
        new AlertDialog.Builder(this).setTitle("Recent PDFs").setItems(names,(d,which)->openPdf(Uri.parse(uris[which]))).setNegativeButton("Close",null).show();
    }
    private void showTools(){
        String[] items={"Merge PDFs","Extract current page","Rotate current page 90°","Add typed signature / text","Images to PDF"};
        new AlertDialog.Builder(this).setTitle("PDF tools")
          .setItems(items,(dialog,n)->{
            if(n==0){pickMerge();return;}
            if(n==4){pickImages();return;}
            if(documentUri==null){toast("Open a PDF first");return;}
            exportPageIndex=pageIndex;
            if(n==1)requestSave("extract","JD-Page-"+(pageIndex+1)+".pdf");
            if(n==2)requestSave("rotate","JD-Rotated.pdf");
            if(n==3)askForStamp();
          }).show();
    }
    private void askForStamp(){
        EditText input=new EditText(this);
        input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Your name or approved text");
        LinearLayout wrapper=new LinearLayout(this);wrapper.setPadding(dp(22),dp(8),dp(22),0);wrapper.addView(input);
        new AlertDialog.Builder(this).setTitle("Add typed text").setMessage("Adds a visible text stamp to the bottom-left of this page. This is not a certified digital signature.")
          .setView(wrapper).setNegativeButton("Cancel",null)
          .setPositiveButton("Save as PDF",(d,w)->{
            stampText=input.getText().toString().trim();
            if(stampText.length()==0){toast("Enter text first");return;}
            requestSave("stamp","JD-Stamped.pdf");
          }).show();
    }
    private void searchText(){
        if(documentUri==null){toast("Open a PDF first");return;}
        EditText input=new EditText(this); input.setSingleLine(true);input.setHint("Find words in PDF");
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(22),dp(9),dp(22),0);wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Search PDF text").setView(wrap).setNegativeButton("Cancel",null)
        .setPositiveButton("Find",(d,n)->{
            final String query=input.getText().toString().trim();
            if(query.isEmpty())return;
            final Uri doc=documentUri;final int start=pageIndex;
            toast("Searching…");
            jobs.execute(()->{
                try(InputStream is=getContentResolver().openInputStream(doc);
                    PDDocument pdf=PDDocument.load(is)){
                    PDFTextStripper stripper=new PDFTextStripper();
                    int found=-1;
                    for(int k=0;k<pdf.getNumberOfPages();k++){
                        int idx=(start+k)%pdf.getNumberOfPages();
                        stripper.setStartPage(idx+1); stripper.setEndPage(idx+1);
                        if(stripper.getText(pdf).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))){
                            found=idx;break;
                        }
                    }
                    final int page=found;
                    runOnUiThread(()->{
                        if(!doc.equals(documentUri))return;
                        if(page>=0){pageIndex=page; renderPage(); toast("Found on page "+(page+1));}
                        else alert("Not found","No matching selectable text. Scanned PDFs require OCR.");
                    });
                }catch(Exception e){ runOnUiThread(()->alert("Search error",shortError(e))); }
            });
        }).show();
    }
    private void requestSave(String kind,String defaultName){
        exportKind=kind; Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,defaultName);startActivityForResult(i,SAVE_PDF);
    }
    private void executeExport(Uri output){
        final String kind=exportKind;
        final Uri source=documentUri;
        final int page=exportPageIndex;
        final String label=stampText;
        final ArrayList<Uri> selected=new ArrayList<>(inputUris);
        toast("Processing PDF locally…");
        jobs.execute(()->{
            try(OutputStream os=getContentResolver().openOutputStream(output,"w")){
                if(os==null)throw new IOException("Cannot write output");
                if("merge".equals(kind))exportMerge(selected,os);
                else if("images".equals(kind))exportImages(selected,os);
                else if(source==null)throw new IOException("No PDF opened");
                else exportSingle(source,page,kind,label,os);
                os.flush();
                runOnUiThread(()->new AlertDialog.Builder(this)
                    .setTitle("PDF saved").setMessage("Saved successfully to your selected location.")
                    .setNegativeButton("Done",null)
                    .setPositiveButton("Open",(d,n)->openPdf(output)).show());
            }catch(Exception e){runOnUiThread(()->alert("Export failed",shortError(e)));}
        });
    }
    private void exportMerge(ArrayList<Uri> list,OutputStream os)throws Exception{
        PDFMergerUtility merger=new PDFMergerUtility();
        try(PDDocument combined=new PDDocument()){
            for(Uri u:list){
                try(InputStream is=getContentResolver().openInputStream(u);
                    PDDocument doc=PDDocument.load(is)){
                    merger.appendDocument(combined,doc);
                }
            }
            if(combined.getNumberOfPages()==0)throw new IOException("Nothing to merge");
            combined.save(os);
        }
    }
    private void exportSingle(Uri src,int index,String kind,String label,OutputStream os)throws Exception{
        try(InputStream is=getContentResolver().openInputStream(src);
            PDDocument pdf=PDDocument.load(is)){
            if(index<0||index>=pdf.getNumberOfPages())throw new IOException("Invalid page");
            if("extract".equals(kind)){
                try(PDDocument one=new PDDocument()){
                    one.importPage(pdf.getPage(index)); one.save(os);
                }
            }else{
                PDPage p=pdf.getPage(index);
                if("rotate".equals(kind))p.setRotation((p.getRotation()+90)%360);
                if("stamp".equals(kind)){
                    String safe=label.replaceAll("[^\\x20-\\x7E]","?");
                    if(safe.length()>90)safe=safe.substring(0,90);
                    try(PDPageContentStream cs=new PDPageContentStream(pdf,p,PDPageContentStream.AppendMode.APPEND,true,true)){
                        cs.beginText();cs.setFont(PDType1Font.HELVETICA_BOLD,15);
                        cs.newLineAtOffset(36,36);cs.showText(safe);cs.endText();
                    }
                }
                pdf.save(os);
            }
        }
    }
    private void exportImages(ArrayList<Uri> imgs,OutputStream os)throws Exception{
        PdfDocument pdf=new PdfDocument();
        try{
            int pageNum=1;
            for(Uri uri:imgs){
                BitmapFactory.Options opts=new BitmapFactory.Options();
                opts.inJustDecodeBounds=true;
                try(InputStream is=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(is,null,opts);}
                int sample=1;while(opts.outWidth/sample>2200 || opts.outHeight/sample>3000)sample*=2;
                opts.inJustDecodeBounds=false;opts.inSampleSize=sample;
                Bitmap bitmap;
                try(InputStream is=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(is,null,opts);}
                if(bitmap==null)continue;
                PdfDocument.PageInfo inf=new PdfDocument.PageInfo.Builder(595,842,pageNum++).create();
                PdfDocument.Page p=pdf.startPage(inf);
                Canvas c=p.getCanvas();c.drawColor(Color.WHITE);
                float scale=Math.min(555f/bitmap.getWidth(),802f/bitmap.getHeight());
                float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
                RectF r=new RectF((595-w)/2,(842-h)/2,(595+w)/2,(842+h)/2);
                c.drawBitmap(bitmap,null,r,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
                pdf.finishPage(p);bitmap.recycle();
            }
            if(pageNum==1)throw new IOException("No images could be opened");
            pdf.writeTo(os);
        }finally{pdf.close();}
    }
    private String shortError(Exception e){
        String s=e.getMessage();return s==null?e.getClass().getSimpleName():s;
    }
    @Override protected void onDestroy(){
        closeDocument();jobs.shutdown(); super.onDestroy();
    }
}

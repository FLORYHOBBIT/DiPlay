package com.shilapi.xcertplay.aion;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Grant-only, read-only access to one hash-named prepared cover; no general file roots or HTTP server. */
public final class InstrumentArtworkProvider extends ContentProvider {
    private static final String FOLDER="aion-instrument-art";
    public static Uri uri(Context context,File file){
        checkFile(context,file);
        return new Uri.Builder().scheme("content").authority(context.getPackageName()+".instrument-art")
            .appendPath("artwork").appendPath(file.getName()).build();
    }
    private static File checkFile(Context context,File file){
        if(!file.getName().matches("[0-9a-f]{64}\\.png"))throw new SecurityException("Invalid artwork filename");
        try{
            File directory=new File(context.getCacheDir(),FOLDER).getCanonicalFile();
            File actual=file.getCanonicalFile();
            if(!directory.equals(actual.getParentFile()))throw new SecurityException("Artwork path outside cache");
            return actual;
        }catch(IOException error){throw new SecurityException("Cannot resolve artwork",error);}
    }
    private File file(Uri uri){
        if(!"content".equals(uri.getScheme()) || !(getContext().getPackageName()+".instrument-art").equals(uri.getAuthority())
            || uri.getPathSegments().size()!=2 || !"artwork".equals(uri.getPathSegments().get(0)))throw new SecurityException("Invalid artwork URI");
        return checkFile(getContext(),new File(new File(getContext().getCacheDir(),FOLDER),uri.getLastPathSegment()));
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){file(uri);return "image/png";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new SecurityException("Artwork is read-only");
        File target=file(uri);if(!target.isFile())throw new FileNotFoundException("Artwork no longer available");
        return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        File target=file(uri);
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor result=new MatrixCursor(columns);Object[] values=new Object[columns.length];
        for(int i=0;i<columns.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=target.getName();
            else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=target.length();
        }
        if(target.isFile())result.addRow(values);return result;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only artwork");}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException("Read-only artwork");}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException("Read-only artwork");}
}

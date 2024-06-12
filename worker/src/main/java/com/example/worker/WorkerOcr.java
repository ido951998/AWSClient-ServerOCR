package com.example.worker;

import java.io.File;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;


import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;


public class WorkerOcr {

    public String imageDownloader(String urlstr) throws IOException{
        String type = urlstr.split("\\.")[urlstr.split("\\.").length-1];
        URL url = new URL(urlstr);
        ReadableByteChannel rbc = Channels.newChannel(url.openStream());
        FileOutputStream fos = new FileOutputStream("./image." + type);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        fos.close();
        rbc.close();
        return type;
    }

    public String doOcr(String type) throws TesseractException{
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/home/ohalf/Tess4J/tessdata");
        File f = new File("./image." + type);
        return tesseract.doOCR(f);
    }

    public void imageDelete(String type){
        File image = new File("./image." + type);
        image.delete();
    }

    public static void main(String[] args) throws IOException, TesseractException {
        WorkerOcr w = new WorkerOcr();
        w.imageDownloader("http://files.microscan.com/Technology/OCR/ocr_font_examples.jpg");
        System.out.println(w.doOcr("jpg"));
        w.imageDelete("jpg");
    }
}

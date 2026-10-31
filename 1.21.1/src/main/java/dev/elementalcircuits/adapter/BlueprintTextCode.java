package dev.elementalcircuits.adapter;

import dev.elementalcircuits.domain.Blueprint;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

/** Bounded, versioned transport for configuration only. JSON remains the server validation format. */
public final class BlueprintTextCode {
    public static final int LIMIT=90000;
    private BlueprintTextCode(){ }
    public static String encode(Blueprint blueprint){
        byte[] json=JsonCodecs.blueprint(blueprint).getBytes(StandardCharsets.UTF_8);
        if(json.length>JsonCodecs.BLUEPRINT_LIMIT)throw new IllegalArgumentException("蓝图过大");
        Deflater deflater=new Deflater(9);ByteArrayOutputStream output=new ByteArrayOutputStream();
        try{
            deflater.setInput(json);deflater.finish();byte[] buffer=new byte[2048];
            while(!deflater.finished()){int n=deflater.deflate(buffer);output.write(buffer,0,n);}
        }finally{deflater.end();}
        CRC32 crc=new CRC32();crc.update(json);
        return "EC1."+Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())+"."+String.format(Locale.ROOT,"%08x",crc.getValue());
    }
    public static Blueprint decode(String input){
        if(input==null || input.length()>LIMIT)throw new IllegalArgumentException("文本码过长或为空");
        String text=input.strip();if(text.startsWith("{"))return JsonCodecs.readBlueprint(text); // Previous JSON exports.
        String[] parts=text.split("\\.",-1);
        if(parts.length!=3 || !parts[0].equals("EC1") || !parts[1].matches("[A-Za-z0-9_-]+") || !parts[2].matches("[0-9a-fA-F]{8}"))throw new IllegalArgumentException("无法识别文本码，请完整粘贴EC1文本码");
        byte[] compressed;
        try{compressed=Base64.getUrlDecoder().decode(parts[1]);}catch(IllegalArgumentException e){throw new IllegalArgumentException("文本码内容损坏");}
        Inflater inflater=new Inflater();ByteArrayOutputStream output=new ByteArrayOutputStream();
        try{
            inflater.setInput(compressed);byte[] buffer=new byte[2048];
            while(!inflater.finished()){
                int n=inflater.inflate(buffer);
                if(output.size()+n>JsonCodecs.BLUEPRINT_LIMIT)throw new IllegalArgumentException("蓝图解码后过大");
                output.write(buffer,0,n);
                if(n==0 && !inflater.finished())throw new IllegalArgumentException("文本码不完整或压缩格式不支持");
            }
            if(inflater.getRemaining()!=0)throw new IllegalArgumentException("文本码含多余数据");
        }catch(DataFormatException e){throw new IllegalArgumentException("文本码压缩内容损坏");}finally{inflater.end();}
        byte[] json=output.toByteArray();CRC32 crc=new CRC32();crc.update(json);
        if(crc.getValue()!=Long.parseUnsignedLong(parts[2],16))throw new IllegalArgumentException("文本码校验失败，请重新复制");
        try{return JsonCodecs.readBlueprint(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(json)).toString());}
        catch(CharacterCodingException e){throw new IllegalArgumentException("文本码字符编码损坏");}
    }
}

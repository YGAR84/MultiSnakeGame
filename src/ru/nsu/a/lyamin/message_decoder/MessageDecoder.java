//package ru.nsu.a.lyamin.message_decoder;
//
//import com.google.protobuf.InvalidProtocolBufferException;
//
//public class MessageDecoder
//{
//
//    public static SnakesProto.GameMessage decodeMessage(byte[] mess)
//    {
//        SnakesProto.GameMessage result = null;
//        try
//        {
//            result = SnakesProto.GameMessage.parseFrom(mess);
//        }
//        catch (InvalidProtocolBufferException e)
//        {
//            e.printStackTrace();
//        }
//
//        return result;
//    }
//
//    public static byte[] encodeMessage(SnakesProto.GameMessage mess)
//    {
//        return mess.toByteArray();
//    }
//
//}

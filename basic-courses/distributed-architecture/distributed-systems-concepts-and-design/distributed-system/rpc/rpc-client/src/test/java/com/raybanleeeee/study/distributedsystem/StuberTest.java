//package com.raybanleeeee.study.distributedsystem;
//
//import com.alibaba.fastjson.JSONObject;
//import org.junit.Test;
//import org.omg.CORBA.StringHolder;
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Method;
//import java.util.Arrays;
//
//public class StuberTest {
//
//    public static class AClass{
//        public static void method(int a, String b){
//            ;
//        }
//    }
//
//    @Test
//    public void getRemoteInstanceTest(){
//
//        try {
//            Method method = AClass.class.getDeclaredMethod("method", int.class, String.class);
//            //Stuber.getRemoteInstance(method);
//        }catch(NoSuchMethodException e){
//            e.printStackTrace();
//        }
//    }
//
//    static class ClassA {
//
//        private static int staticVal = 111;
//
//        public int a = 1;
//        public ClassB [] b = Arrays.asList(new ClassB(), new ClassB()).toArray(new ClassB[0]) ;
//
//    }
//
//    static  class ClassB {
//        public Integer b = 1;
//        public String a = new String("Hello world!");
//        public ClassC c = new ClassC();
//    }
//
//
//    static  class ClassC {
//        public int a = 1;
//    }
//
//    @Test
//    public void testJson(){
//
//        ClassA objects [] = { new ClassA(), new ClassA()};
//
//        String json = JSONObject.toJSONString(objects);
//        System.out.println(json);
//
//        objects = JSONObject.parseObject(json, ClassA[].class);
//    }
//
//
//    @Test
//    public void encodeTest(){
//
//        try {
//            Method getMethodSign = RpcStuber.class.getDeclaredMethod("getMethodSign", Method.class);
//            Method encode = RpcStuber.class.getDeclaredMethod("encode", String.class, String.class);
//            Method decode = RpcStuber.class.getDeclaredMethod("decode", byte[].class, int.class, StringHolder.class);
//
//            getMethodSign.setAccessible(true);
//            encode.setAccessible(true);
//            decode.setAccessible(true);
//
//            RpcStuber stuber = new RpcStuber(null,null);
//
//            for(Method mt: String.class.getDeclaredMethods()) {
//
//                String methodSign = (String)getMethodSign.invoke(stuber, mt);
//                byte[] message = (byte[])encode.invoke(stuber, methodSign, "asdfdassdfsd");
//                StringHolder methodSignHolder = new StringHolder();
//                String decodedMessage = (String)decode.invoke(stuber, message, message.length, methodSignHolder);
//
//                System.out.println(methodSignHolder.value+ " "+ decodedMessage);
//
//            }
//
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        }
//
//    }
//
//    @Test
//    public void testEncodeDecode(){
//        int i = 0;
//        for(Method method: String.class.getDeclaredMethods()){
//
//            RpcStuber.RpcClientProtocol clientSend = new RpcStuber.RpcClientProtocol(
//                    i++,
//                    method,
//                    new ClassA()
//            );
//
//            byte[] encodeRpcClientProtocol = clientSend.encode();
//
//            RpcStuber.RpcClientProtocol serverRecvProtocol = new RpcStuber.RpcClientProtocol();
//            RpcStuber.RpcServerProtocol.decode(serverRecvProtocol, encodeRpcClientProtocol);
//
//            System.out.print(serverRecvProtocol.methodId+" ");
//            System.out.print(serverRecvProtocol.methodSignLen+" ");
//            System.out.println(new String(serverRecvProtocol.methodSign));
//            System.out.print(serverRecvProtocol.paramsJsonLen+" ");
//            System.out.println(new String(serverRecvProtocol.paramsJson));
//            System.out.println();
//
//            RpcStuber.RpcServerProtocol serverSendProtocol = new RpcStuber.RpcServerProtocol(
//                    RpcStuber.RpcServerProtocol.STATUS_SUCCESS,
//                    new String(serverRecvProtocol.paramsJson, 0, serverRecvProtocol.paramsJsonLen)
//            );
//            byte[] encodeRpcServerProtocol = serverSendProtocol.encode();
//
//            RpcStuber.RpcServerProtocol clientRecvProtocol = new RpcStuber.RpcServerProtocol();
//            RpcStuber.RpcClientProtocol.decode(clientRecvProtocol, encodeRpcServerProtocol);
//
//            System.out.print(clientRecvProtocol.status+" ");
//            System.out.print(clientRecvProtocol.returnDataLen+" ");
//            System.out.println(new String(clientRecvProtocol.returnData));
//            System.out.println();
//
//        }
//    }
//}

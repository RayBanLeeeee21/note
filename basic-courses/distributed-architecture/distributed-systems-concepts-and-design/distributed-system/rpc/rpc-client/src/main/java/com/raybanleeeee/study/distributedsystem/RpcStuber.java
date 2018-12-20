package com.raybanleeeee.study.distributedsystem;

import com.alibaba.fastjson.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.HashMap;

public class RpcStuber {

    private DatagramSocket localSocket;
    private Communicator communicator;

    private HashMap<String, InetSocketAddress> mapMethodToInetAddr = new HashMap<>();

    public RpcStuber(DatagramSocket localSocket, Communicator communicator) {
        this.localSocket = localSocket;
        this.communicator = communicator;
    }

    private InetSocketAddress queryServerHost(String methodSign) {

        InetSocketAddress serverAddr = mapMethodToInetAddr.get(methodSign);

        if (serverAddr == null) {

        }

        return serverAddr;
    }

    private static class RpcClientProtocol {

        private int methodId;
        private short methodSignLen;
        private byte[] methodSign;
        private short paramsJsonLen;
        private byte[] paramsJson;

        public RpcClientProtocol() {
        }

        public RpcClientProtocol(int methodId, Method method, Object params) {
            this.methodId = methodId;
            this.methodSign = method.toString().getBytes();
            this.methodSignLen = (short) methodSign.length;
            this.paramsJson = JSONObject.toJSONString(params).getBytes();
            this.paramsJsonLen = (short) this.paramsJson.length;
        }

        public byte[] encode() {
            return encode(this);
        }

        public static byte[] encode(RpcClientProtocol protocol) {

            ByteArrayOutputStream out = new ByteArrayOutputStream(protocol.methodSignLen + protocol.paramsJsonLen + 8);

            // encode methodId
            ByteUtils.writeInt(out, protocol.methodId);

            // encode methodSignLen
            ByteUtils.writeShort(out, protocol.methodSignLen);

            // encode methodSign
            out.write(protocol.methodSign, 0, protocol.methodSignLen);

            // encode paramsJsonLen
            ByteUtils.writeShort(out, protocol.paramsJsonLen);

            // encode methodSign
            out.write(protocol.paramsJson, 0, protocol.paramsJsonLen);

            return out.toByteArray();
        }

        public static boolean decode(RpcServerProtocol protocal, byte[] encodedProtocol) {

            ByteArrayInputStream in = new ByteArrayInputStream(encodedProtocol);

            // decode status
            byte status = (byte) in.read();
            if (status != RpcServerProtocol.STATUS_SUCCESS)
                return false;
            protocal.status = status;

            // decode returnDataLen
            protocal.returnDataLen = ByteUtils.readShort(in);

            // decode returnData
            protocal.returnData = new byte[protocal.returnDataLen];
            in.read(protocal.returnData, 0, protocal.returnDataLen);

            return true;
        }
    }

    private static class RpcServerProtocol {

        public static final byte STATUS_SUCCESS = 0;

        private byte status;
        private short returnDataLen;
        private byte[] returnData;

        public RpcServerProtocol() {
        }

        public RpcServerProtocol(byte status, String returnData) {
            this.status = status;
            this.returnData = returnData.getBytes();
            this.returnDataLen = (short) this.returnData.length;
        }

        public byte[] encode() {
            return encode(this);
        }

        public static byte[] encode(RpcServerProtocol protocol) {

            ByteArrayOutputStream out = new ByteArrayOutputStream(3 + protocol.returnDataLen);

            // encode status
            out.write(protocol.status);

            // encode returnDataLen
            ByteUtils.writeShort(out, protocol.returnDataLen);

            // encode methodSign
            out.write(protocol.returnData, 0, protocol.returnDataLen);

            return out.toByteArray();
        }

        public static void decode(RpcClientProtocol protocol, byte[] encodedProtocol) {
            ByteArrayInputStream in = new ByteArrayInputStream(encodedProtocol);

            // decode method id
            protocol.methodId = ByteUtils.readInt(in);

            // decode methodSignLen
            protocol.methodSignLen = ByteUtils.readShort(in);

            // decode methodSign
            protocol.methodSign = new byte[protocol.methodSignLen];
            in.read(protocol.methodSign, 0, protocol.methodSignLen);

            // decode returnDataLen
            protocol.paramsJsonLen = ByteUtils.readShort(in);

            // decode returnData
            protocol.paramsJson = new byte[protocol.paramsJsonLen];
            in.read(protocol.paramsJson, 0, protocol.paramsJsonLen);

        }
    }
}

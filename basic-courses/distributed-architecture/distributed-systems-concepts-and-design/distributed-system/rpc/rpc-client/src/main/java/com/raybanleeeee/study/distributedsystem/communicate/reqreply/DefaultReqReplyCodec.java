
package com.raybanleeeee.study.distributedsystem.communicate.reqreply;



import java.io.*;
import java.net.DatagramPacket;
import java.net.SocketAddress;

public class DefaultReqReplyCodec implements ReqReplyCodec {


    private byte[] reqReplyMessageToBytes(ReqReplyMessage message){

        byte[] messageBytes = null;
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
            objOut.writeObject(message);
            messageBytes = byteOut.toByteArray();

        } catch (Exception e) {
        }
        return messageBytes;
    }

    private ReqReplyMessage bytesToReqReplyMessage(byte[]messageBytes, int offset, int length) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(messageBytes, offset, length);
        ObjectInputStream objIn = new ObjectInputStream(byteIn);
        return (ReqReplyMessage) objIn.readObject();
    }

    @Override
    public byte[] encodeRequestToBytes(ReqReplyMessage request) {

        if(request.getMessageType()!=ReqReplyMessage.MESSAGE_TYPE_REQUEST)
            throw new IllegalArgumentException("request.messageType should be ReqReplyMessage.MESSAGE_TYPE_REQUEST");
        return reqReplyMessageToBytes(request);
    }

    @Override
    public DatagramPacket encodeRequestToUdp(
            ReqReplyMessage request,
            SocketAddress recvAddr
    ) {
        if(request.getMessageType()!=ReqReplyMessage.MESSAGE_TYPE_REQUEST)
            throw new IllegalArgumentException("request.messageType should be ReqReplyMessage.MESSAGE_TYPE_REQUEST");

        byte[] requestBytes = encodeRequestToBytes(request);
        return new DatagramPacket(
                requestBytes,
                requestBytes.length,
                recvAddr
        );
    }

    @Override
    public DatagramPacket encodeRequestToUdp(
            int requestId,
            byte[] message,
            SocketAddress recvAddr
    ){
        return encodeRequestToUdp(
                new ReqReplyMessage(ReqReplyMessage.MESSAGE_TYPE_REQUEST, requestId, message),
                recvAddr
        );
    }

    @Override
    public ReqReplyMessage decodeRequestBytes(byte[] data, int offset, int length) throws IOException, ClassNotFoundException {
        ReqReplyMessage request = bytesToReqReplyMessage(data, offset, length);

        if(request.getMessageType()!=ReqReplyMessage.MESSAGE_TYPE_REQUEST)
            throw new IllegalArgumentException("request.messageType should be ReqReplyMessage.MESSAGE_TYPE_REQUEST");

        return request;
    }

    @Override
    public ReqReplyMessage decodeRequestUdp(DatagramPacket messageUdp) throws IOException, ClassNotFoundException {
        byte[] messageBytes = messageUdp.getData();
        return decodeRequestBytes(messageBytes, 0, messageBytes.length);
    }

    @Override
    public byte[] encodeResponseToBytes(ReqReplyMessage response){
        if(response.getMessageType()!=ReqReplyMessage.MESSAGE_TYPE_RESPONSE)
            throw new IllegalArgumentException("response.messageType should be ReqReplyMessage.MESSAGE_TYPE_RESPONSE");

        return reqReplyMessageToBytes(response);
    }

    @Override
    public DatagramPacket encodeResponseToUdp(
            ReqReplyMessage response,
            SocketAddress recvAddr
    ){
        byte[] responseBytes = encodeResponseToBytes(response);
        return new DatagramPacket(
                responseBytes,
                responseBytes.length,
                recvAddr
        );
    }

    @Override
    public DatagramPacket encodeResponseToUdp(
            int ackId,
            byte[]message,
            SocketAddress recvAddr
    ){
        return encodeResponseToUdp(
                new ReqReplyMessage(ReqReplyMessage.MESSAGE_TYPE_RESPONSE, ackId, message),
                recvAddr
        );
    }

    @Override
    public ReqReplyMessage decodeResponseBytes(
            byte[]responseBytes,
            int lastRequestId
    ) {
        ReqReplyMessage response = null;
        try {
            response = bytesToReqReplyMessage(responseBytes, 0, responseBytes.length);
        }catch (Exception e){
        }
        if(response != null
                && response.getMessageType() == ReqReplyMessage.MESSAGE_TYPE_RESPONSE
                && response.getId()==lastRequestId+1
                ){
            return response;
        }else{
            return null;
        }
    }

    @Override
    public ReqReplyMessage decodeResponseUdp(
            DatagramPacket responseUdp,
            int lastRequestId
    ){
        return decodeResponseBytes(
                responseUdp.getData(),
                lastRequestId
        );
    }
}
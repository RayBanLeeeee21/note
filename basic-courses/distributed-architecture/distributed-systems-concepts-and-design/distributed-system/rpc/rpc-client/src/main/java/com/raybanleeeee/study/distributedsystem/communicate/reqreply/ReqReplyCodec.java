package com.raybanleeeee.study.distributedsystem.communicate.reqreply;

import java.io.*;
import java.net.DatagramPacket;
import java.net.SocketAddress;

public interface ReqReplyCodec {

    
    public byte[] encodeRequestToBytes(ReqReplyMessage request);

    
    public DatagramPacket encodeRequestToUdp(
            ReqReplyMessage request,
            SocketAddress recvAddr
    );

    
    public DatagramPacket encodeRequestToUdp(
            int requestId,
            byte[] message,
            SocketAddress recvAddr
    );

    
    public ReqReplyMessage decodeRequestBytes(byte[] data, int offset, int length) throws IOException, ClassNotFoundException;

    
    public ReqReplyMessage decodeRequestUdp(DatagramPacket messageUdp) throws IOException, ClassNotFoundException;

    
    public byte[] encodeResponseToBytes(ReqReplyMessage response);

    
    public DatagramPacket encodeResponseToUdp(
            ReqReplyMessage response,
            SocketAddress recvAddr
    );

    
    public DatagramPacket encodeResponseToUdp(
            int ackId,
            byte[]message,
            SocketAddress recvAddr
    );

    
    public ReqReplyMessage decodeResponseBytes(
            byte[]responseBytes,
            int lastRequestId
    );
    
    public ReqReplyMessage decodeResponseUdp(
            DatagramPacket responseUdp,
            int lastRequestId
    );
}

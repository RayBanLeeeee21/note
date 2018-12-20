package com.raybanleeeee.study.distributedsystem.communicate.reqreply;

import java.io.Serializable;

public class ReqReplyMessage implements Serializable {

    public static final byte MESSAGE_TYPE_REQUEST = 0;
    public static final byte MESSAGE_TYPE_RESPONSE = 1;

    private byte messageType;
    private int id;
    private byte[] message;

    public ReqReplyMessage(){

    }

    public ReqReplyMessage(
        byte messageType,
        int id,
        byte[] message
    ){
        this.messageType = messageType;
        this.id = id;
        this.message = message;
    }

    /**
     * @return the messageType
     */
    public byte getMessageType() {
        return messageType;
    }

    /**
     * @param messageType the messageType to set
     */
    public void setMessageType(byte messageType) {
        this.messageType = messageType;
    }

    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return the message
     */
    public byte[] getMessage() {
        return message;
    }

    /**
     * @param message the message to set
     */
    public void setMessage(byte[] message) {
        this.message = message;
    }

}

package com.raybanleeeee.study.distributedsystem.communicate.reqreply;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.Arrays;

import com.raybanleeeee.study.distributedsystem.SleepUtils;


import org.junit.Test;

public class ReqReplyProtocolTest {

    @Test
    public void testReqReplyClient() {

        Thread t1 = new Thread(() -> {

            try {
                DatagramSocket localSocket = new DatagramSocket(4000);
                localSocket.setSoTimeout(1000);
                InetSocketAddress serverAddr = new InetSocketAddress(Inet4Address.getLocalHost(), 5000);

                ReqReplyProtocol.ReqReplyClient client = ReqReplyProtocol.newReqReplyClient(
                    localSocket, 
                    serverAddr
                );

                byte[] result = client.request("hello world!".getBytes());

                System.out.println(Thread.currentThread().getName()+": "+ new String(result));
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {

            try {

                DatagramSocket localSocket = new DatagramSocket(5000);

                ReqReplyProtocol.ReqReplyServer server = ReqReplyProtocol.newReqReplyServer(
                    localSocket, 
                    data-> {
                        byte[] result = Arrays.copyOf(data, data.length);
                        for(int i = 0; i<result.length; i++){
                            result[i]++;
                        }
                        return result;
                    }
                );

                byte[] message = server.response();
                System.out.println(Thread.currentThread().getName()+": "+new String(message));
        
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t2.start();
        SleepUtils.sleep(50);

        t1.start();

        try{
            t1.join();
            t2.join();
        }catch(Exception e){

        }

    }
}
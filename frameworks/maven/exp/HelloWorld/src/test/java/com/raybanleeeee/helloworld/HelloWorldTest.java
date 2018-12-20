package com.raybanleeeee.helloworld;

import org.junit.Test;

import junit.framework.Assert;

public class HelloWorldTest{
    
    @Test
    public void testSayHello(){
        HelloWorld helloWorld = new HelloWorld();
        String str = helloWorld.sayHello();
        System.out.println(str);

    }

    @Test
    public void testJdkVersion(){
        Runnable lambdaTest = ()->{
            System.out.println("lamda is supported in JDK 1.8.");
        };
        lambdaTest.run();
    }
}
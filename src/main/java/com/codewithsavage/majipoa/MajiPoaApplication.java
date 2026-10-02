package com.codewithsavage.majipoa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MajiPoaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MajiPoaApplication.class, args);

        for (int x = 1; x < 11; x++ ){
            System.out.println("hello/n");
        }
    }

}

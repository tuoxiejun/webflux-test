package com.example.gateway.model;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class GatewayRequest {

    @NotNull
    @Valid
    private Head head;

    @NotNull
    @Valid
    private Body body;

    public Head getHead() {
        return head;
    }

    public void setHead(Head head) {
        this.head = head;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public static class Head {
        @NotBlank
        @Size(max = 32)
        private String requestNo;

        @NotBlank
        @Pattern(regexp = "\\\\d{8}")
        private String date;

        @NotBlank
        @Pattern(regexp = "\\\\d{2}:\\\\d{2}:\\\\d{2}")
        private String time;

        public String getRequestNo() {
            return requestNo;
        }

        public void setRequestNo(String requestNo) {
            this.requestNo = requestNo;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }
    }

    public static class Body {
        @NotBlank
        @Size(max = 8)
        private String vol1;

        @NotBlank
        @Size(max = 8)
        private String vol2;

        @NotBlank
        @Size(max = 8)
        private String vol3;

        @NotNull
        @Valid
        private List<ArrayItem> array;

        public String getVol1() {
            return vol1;
        }

        public void setVol1(String vol1) {
            this.vol1 = vol1;
        }

        public String getVol2() {
            return vol2;
        }

        public void setVol2(String vol2) {
            this.vol2 = vol2;
        }

        public String getVol3() {
            return vol3;
        }

        public void setVol3(String vol3) {
            this.vol3 = vol3;
        }

        public List<ArrayItem> getArray() {
            return array;
        }

        public void setArray(List<ArrayItem> array) {
            this.array = array;
        }
    }

    public static class ArrayItem {
        @NotBlank
        @Size(max = 8)
        private String arraykey;

        @NotBlank
        @Size(max = 8)
        private String arrayvalue;

        public String getArraykey() {
            return arraykey;
        }

        public void setArraykey(String arraykey) {
            this.arraykey = arraykey;
        }

        public String getArrayvalue() {
            return arrayvalue;
        }

        public void setArrayvalue(String arrayvalue) {
            this.arrayvalue = arrayvalue;
        }
    }
}

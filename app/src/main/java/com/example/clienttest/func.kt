package com.example.clienttest

class func {



    fun calC(body: bodyData) : String {
        var cHEX = body.c.toString(16)
        when(cHEX.substring(0,2)) {
            "46" -> {
                if(cHEX.substring(2,4) == "41") return "FA (전진 자동)"
                else if(cHEX.substring(2,4) == "31") return "F1 (전진 1단)"
                else if(cHEX.substring(2,4) == "32") return "F2 (전진 2단)"
                else if(cHEX.substring(2,4) == "33") return "F3 (전진 3단)"
                else if(cHEX.substring(2,4) == "34") return "F4 (전진 4단)"
                else if(cHEX.substring(2,4) == "35") return "F5 (전진 5단)"
                else if(cHEX.substring(2,4) == "36") return "F6 (전진 6단)"
                else return "NULL"
            }
            "52" -> {
                if(cHEX.substring(2,4) == "41") return "RA (후진 자동)"
                else if(cHEX.substring(2,4) == "31") return "R1 (후진 1단)"
                else if(cHEX.substring(2,4) == "32") return "R2 (후진 2단)"
                else if(cHEX.substring(2,4) == "33") return "R3 (후진 3단)"
                else return "NULL"
            }
            "4e" -> {
                if (cHEX.substring(2,4) == "20") return "N_ (중립)"
                else return "NULL"
            }
            "50" -> {
                if (cHEX.substring(2,4) == "20") return "P_ (피벗)"
                else return "NULL"
            }
            else -> return "NULL"
        }

    }

    fun calI(body: bodyData) : String {
        if (body.i == 0) return "해제"
        else if (body.i == 1) return "잠김"
        else return "NULL"
    }



    fun calK(body: bodyData) :String {
        var kHEX = body.k.toString(16)
        when(kHEX.substring(0,2)) {
            "aa" -> {
                if (kHEX.substring(2, 4) == "01") return "전방낮춤 상태"
                else if (kHEX.substring(2,4) == "02") return "후방낮춤 상태"
                else if (kHEX.substring(2,4) == "03") return "좌측낮춤 상태"
                else if (kHEX.substring(2,4) == "04") return "우측낮춤 상태"
                else if (kHEX.substring(2,4) == "05") return "차량높임 상태"
                else if (kHEX.substring(2,4) == "06") return "차량낮춤 1단계 상태"
                else if (kHEX.substring(2,4) == "07") return "차량낮춤 2단계 상태"
                else if (kHEX.substring(2,4) == "08") return "차량낮춤 3단계 상태"
                else if (kHEX.substring(2,4) == "09") return "자세복귀 상태"
                else if (kHEX.substring(2,4) == "10") return "ISU 질소압 점검 및 보충준비 상태"
                else if (kHEX.substring(2,4) == "0d") return "자세복귀 강제수행"
                else if (kHEX.substring(2,4) == "0e") return "자세높임 강제수행"
                else return "NULL"
            }
            "ff" -> return "현수장치 이상"
            else -> return "NULL"
        }
    }


    fun calL(body: bodyData) : String {
        var yHEX = body.l.toString(16)
        when (yHEX.substring(0,2)) {
            "aa" -> {
                if(yHEX.substring(2,4) == "0a") return "현수장치 정상/장력풀림 ON(장력풀림) 상태"
                else if (yHEX.substring(2,4) == "0b") return "현수장치 정상/장력풀림 OFF(장력인가) 상태"
                else return "NULL"
            }
            "ff" -> {
                if(yHEX.substring(2,4) == "0a") return "현수장치 정상/장력풀림 ON(장력풀림) 상태"
                else if (yHEX.substring(2,4) == "0b") return "현수장치 정상/장력풀림 OFF(장력인가) 상태"
                else return "NULL"
            }
            else -> return "NULL"
        }
    }


    fun calN(turret: turretData) : String{
        if (turret.n == 0) return "포구동장치전원 끔"
        else if(turret.n == 1) return "포구동장치전원 켬"
        else return "NULL"
    }


    fun calV(turret: turretData) : String {
        if (turret.v == 0) return "정상"
        else if (turret.v == 1) return "주포고정"
        else return "NULL"
    }


    fun calW(turret: turretData) : String {
        if (turret.w == 0) return "열림"
        else if (turret.w == 1) return "닫힘"
        else return "NULL"
    }


    fun calX(turret: turretData) : String {
        if (turret.x ==0) return "사격금지"
        else if (turret.x == 1) return "사격가능"
        else if (turret.x == 2) return "주간사격금지"
        else if (turret.x == 3) return "주간사격가능"
        else return "NULL"
    }


}
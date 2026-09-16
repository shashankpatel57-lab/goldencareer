package com.goldencareer.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenStore(private val context:Context){
    private val prefs=context.getSharedPreferences("gc_secure",Context.MODE_PRIVATE)
    private val alias="golden_career_token_key"
    private fun key():SecretKey{
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(alias,null) as? SecretKey)?.let{return it}
        val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }
    private fun encrypt(v:String):String{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.iv+c.doFinal(v.toByteArray()),Base64.NO_WRAP)}
    private fun decrypt(v:String?):String?=runCatching{if(v.isNullOrBlank())return null;val raw=Base64.decode(v,Base64.NO_WRAP);val iv=raw.copyOfRange(0,12);val data=raw.copyOfRange(12,raw.size);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv));String(c.doFinal(data))}.getOrNull()
    fun saveTokens(access:String,refresh:String?=null){val e=prefs.edit().putString("access",encrypt(access));if(!refresh.isNullOrBlank())e.putString("refresh",encrypt(refresh));e.apply()}
    fun accessToken():String?=decrypt(prefs.getString("access",null))
    fun refreshToken():String?=decrypt(prefs.getString("refresh",null))
    fun clear(){prefs.edit().clear().apply()}
}

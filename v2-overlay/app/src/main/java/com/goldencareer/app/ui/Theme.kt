package com.goldencareer.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy=Color(0xFF07182D)
val Navy2=Color(0xFF0D2D50)
val Navy3=Color(0xFF174A79)
val Gold=Color(0xFFE5AA37)
val GoldSoft=Color(0xFFFFD77F)
val Cream=Color(0xFFFBF6EA)
val Ink=Color(0xFF07111F)
val Muted=Color(0xFF657487)
val Line=Color(0xFFE2E8F0)
val Soft=Color(0xFFF5F7FA)
val Success=Color(0xFF08795D)
val Danger=Color(0xFFB93333)

private val scheme=lightColorScheme(primary=Navy,onPrimary=Color.White,secondary=Gold,onSecondary=Ink,background=Color(0xFFF7F9FB),surface=Color.White,onSurface=Ink,surfaceVariant=Cream,outline=Line,error=Danger)
private val typography=Typography(
    headlineLarge=TextStyle(fontSize=34.sp,fontWeight=FontWeight.Black,letterSpacing=(-1.1).sp),
    headlineMedium=TextStyle(fontSize=27.sp,fontWeight=FontWeight.Black,letterSpacing=(-.7).sp),
    titleLarge=TextStyle(fontSize=20.sp,fontWeight=FontWeight.ExtraBold),
    titleMedium=TextStyle(fontSize=16.sp,fontWeight=FontWeight.Bold),
    bodyLarge=TextStyle(fontSize=15.sp),bodyMedium=TextStyle(fontSize=13.sp),labelLarge=TextStyle(fontSize=13.sp,fontWeight=FontWeight.Bold)
)
@Composable fun GoldenCareerTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=scheme,typography=typography,content=content)}

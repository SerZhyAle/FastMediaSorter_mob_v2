package com.sza.fastmediasorter.util.gif

import android.graphics.Bitmap
import java.io.OutputStream

/**
 * AnimatedGifEncoder with proper LZW compression.
 * Based on Kevin Weiner's GIFEncoder with LZW encoding support.
 * 
 * Usage:
 * ```
 * val encoder = AnimatedGifEncoder()
 * encoder.start(outputStream)
 * encoder.setDelay(500) // 500ms between frames
 * encoder.setRepeat(0) // Loop forever
 * encoder.addFrame(bitmap1)
 * encoder.addFrame(bitmap2)
 * encoder.finish()
 * ```
 */
class AnimatedGifEncoder {
    private var out: OutputStream? = null
    private var width = 0
    private var height = 0
    private var delay = 100 // Default 100ms between frames
    private var repeat = 0 // 0 = loop forever
    private var started = false
    private var firstFrame = true
    
    // Color table
    private var colorTab: ByteArray? = null
    private var palSize = 7 // Color table size (2^(palSize+1))
    
    // Indexed pixels
    private var indexedPixels: ByteArray? = null
    private var colorDepth = 0
    
    fun start(os: OutputStream): Boolean {
        out = os
        started = false
        return true
    }
    
    fun setDelay(ms: Int) {
        delay = ms.coerceAtLeast(10) // Min 10ms
    }
    
    fun setRepeat(iter: Int) {
        repeat = iter
    }
    
    fun addFrame(im: Bitmap): Boolean {
        if (out == null) return false
        
        try {
            if (!started) {
                width = im.width
                height = im.height
                writeHeader()
                started = true
            }
            
            analyzePixels(im)
            
            if (firstFrame) {
                writeLSD()
                writePalette()
                writeNetscapeExt()
                firstFrame = false
            }
            
            writeGraphicCtrlExt()
            writeImageDesc()
            writePalette() // Local color table
            writePixels()
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    fun finish(): Boolean {
        if (!started) return false
        
        try {
            started = false
            out?.write(0x3b) // GIF trailer
            out?.flush()
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    private fun analyzePixels(image: Bitmap) {
        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Build color table using simple quantization
        val colorMap = mutableMapOf<Int, Int>()
        val colors = mutableListOf<Int>()
        
        for (pixel in pixels) {
            val rgb = pixel and 0x00FFFFFF
            if (!colorMap.containsKey(rgb) && colors.size < 256) {
                colorMap[rgb] = colors.size
                colors.add(rgb)
            }
        }
        
        // Pad to power of 2
        val tableSize = 256
        colorTab = ByteArray(tableSize * 3)
        
        for (i in colors.indices) {
            val c = colors[i]
            val j = i * 3
            colorTab!![j] = ((c shr 16) and 0xFF).toByte()
            colorTab!![j + 1] = ((c shr 8) and 0xFF).toByte()
            colorTab!![j + 2] = (c and 0xFF).toByte()
        }
        
        colorDepth = 8
        palSize = 7
        
        // Map pixels to color indices
        indexedPixels = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val rgb = pixels[i] and 0x00FFFFFF
            indexedPixels!![i] = (colorMap[rgb] ?: findClosestColor(rgb, colors)).toByte()
        }
    }
    
    private fun findClosestColor(rgb: Int, colors: List<Int>): Int {
        if (colors.isEmpty()) return 0
        
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        
        var minDist = Int.MAX_VALUE
        var minIndex = 0
        
        for (i in colors.indices) {
            val c = colors[i]
            val cr = (c shr 16) and 0xFF
            val cg = (c shr 8) and 0xFF
            val cb = c and 0xFF
            
            val dist = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb)
            if (dist < minDist) {
                minDist = dist
                minIndex = i
            }
        }
        
        return minIndex
    }
    
    private fun writeHeader() {
        out?.write("GIF89a".toByteArray())
    }
    
    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        out?.write(0x80 or palSize) // Global color table flag
        out?.write(0) // Background color index
        out?.write(0) // Pixel aspect ratio
    }
    
    private fun writePalette() {
        colorTab?.let { out?.write(it) }
    }
    
    private fun writeNetscapeExt() {
        out?.write(0x21) // Extension introducer
        out?.write(0xFF) // App extension label
        out?.write(11) // Block size
        out?.write("NETSCAPE2.0".toByteArray())
        out?.write(3) // Sub-block size
        out?.write(1) // Loop sub-block ID
        writeShort(repeat) // Loop count
        out?.write(0) // Block terminator
    }
    
    private fun writeGraphicCtrlExt() {
        out?.write(0x21) // Extension introducer
        out?.write(0xF9) // Graphic control label
        out?.write(4) // Block size
        out?.write(0) // Packed byte
        writeShort(delay / 10) // Delay in 1/100 sec
        out?.write(0) // Transparent color index
        out?.write(0) // Block terminator
    }
    
    private fun writeImageDesc() {
        out?.write(0x2C) // Image separator
        writeShort(0) // Image left
        writeShort(0) // Image top
        writeShort(width) // Image width
        writeShort(height) // Image height
        out?.write(0x80 or palSize) // Local color table flag
    }
    
    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels!!, colorDepth)
        encoder.encode(out!!)
    }
    
    private fun writeShort(value: Int) {
        out?.write(value and 0xFF)
        out?.write((value shr 8) and 0xFF)
    }
}

/**
 * LZW encoder for GIF format.
 */
private class LZWEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixels: ByteArray,
    colorDepth: Int
) {
    private val initCodeSize = colorDepth.coerceAtLeast(2)
    private var remaining = 0
    private var curPixel = 0
    
    // GIF-specific constants
    private val EOF = -1
    private val BITS = 12
    private val HSIZE = 5003
    
    // LZW state
    private var nBits = 0
    private var maxBits = BITS
    private var maxCode = 0
    private var maxMaxCode = 1 shl BITS
    
    private var htab = IntArray(HSIZE)
    private var codetab = IntArray(HSIZE)
    private var hsize = HSIZE
    private var freeEnt = 0
    private var clearFlag = false
    private var gInitBits = 0
    private var clearCode = 0
    private var eofCode = 0
    
    // Output state
    private var curAccum = 0
    private var curBits = 0
    private val masks = intArrayOf(
        0x0000, 0x0001, 0x0003, 0x0007, 0x000F,
        0x001F, 0x003F, 0x007F, 0x00FF, 0x010F,
        0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF,
        0x3FFF, 0x7FFF, 0xFFFF
    )
    
    // Block buffer
    private var aCount = 0
    private var accum = ByteArray(256)
    
    fun encode(os: OutputStream) {
        os.write(initCodeSize) // Write minimum code size
        
        remaining = imgW * imgH
        curPixel = 0
        
        compress(initCodeSize + 1, os)
        
        os.write(0) // Block terminator
    }
    
    private fun compress(initBits: Int, os: OutputStream) {
        gInitBits = initBits
        clearFlag = false
        nBits = gInitBits
        maxCode = maxCode(nBits)
        
        clearCode = 1 shl (initBits - 1)
        eofCode = clearCode + 1
        freeEnt = clearCode + 2
        
        aCount = 0
        
        var ent = nextPixel()
        
        var hshift = 0
        var fcode = hsize
        while (fcode < 65536) {
            hshift++
            fcode *= 2
        }
        hshift = 8 - hshift
        
        val hsizeReg = hsize
        clHash(hsizeReg)
        
        output(clearCode, os)
        
        var c: Int
        outer@ while (nextPixel().also { c = it } != EOF) {
            fcode = (c shl maxBits) + ent
            var i = (c shl hshift) xor ent
            
            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = hsizeReg - i
                if (i == 0) disp = 1
                do {
                    i -= disp
                    if (i < 0) i += hsizeReg
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        continue@outer
                    }
                } while (htab[i] >= 0)
            }
            
            output(ent, os)
            ent = c
            
            if (freeEnt < maxMaxCode) {
                codetab[i] = freeEnt++
                htab[i] = fcode
            } else {
                clBlock(os)
            }
        }
        
        output(ent, os)
        output(eofCode, os)
    }
    
    private fun output(code: Int, os: OutputStream) {
        curAccum = curAccum and masks[curBits]
        curAccum = if (curBits > 0) curAccum or (code shl curBits) else code
        curBits += nBits
        
        while (curBits >= 8) {
            charOut((curAccum and 0xFF).toByte(), os)
            curAccum = curAccum shr 8
            curBits -= 8
        }
        
        if (freeEnt > maxCode || clearFlag) {
            if (clearFlag) {
                maxCode = maxCode(gInitBits.also { nBits = it })
                clearFlag = false
            } else {
                nBits++
                maxCode = if (nBits == maxBits) maxMaxCode else maxCode(nBits)
            }
        }
        
        if (code == eofCode) {
            while (curBits > 0) {
                charOut((curAccum and 0xFF).toByte(), os)
                curAccum = curAccum shr 8
                curBits -= 8
            }
            flushChar(os)
        }
    }
    
    private fun maxCode(nBits: Int): Int = (1 shl nBits) - 1
    
    private fun charOut(c: Byte, os: OutputStream) {
        accum[aCount++] = c
        if (aCount >= 254) flushChar(os)
    }
    
    private fun flushChar(os: OutputStream) {
        if (aCount > 0) {
            os.write(aCount)
            os.write(accum, 0, aCount)
            aCount = 0
        }
    }
    
    private fun clBlock(os: OutputStream) {
        clHash(hsize)
        freeEnt = clearCode + 2
        clearFlag = true
        output(clearCode, os)
    }
    
    private fun clHash(hsize: Int) {
        for (i in 0 until hsize) htab[i] = -1
    }
    
    private fun nextPixel(): Int {
        if (remaining == 0) return EOF
        remaining--
        return pixels[curPixel++].toInt() and 0xFF
    }
}

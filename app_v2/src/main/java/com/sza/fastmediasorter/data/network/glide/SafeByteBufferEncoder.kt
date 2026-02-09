package com.sza.fastmediasorter.data.network.glide
import com.bumptech.glide.load.Encoder
import com.bumptech.glide.load.Options
import java.io.File
import java.io.FileOutputStream
class SafeByteBufferEncoder:Encoder<SafeByteBuffer>{
override fun encode(data:SafeByteBuffer,file:File,options:Options):Boolean{
return try{
FileOutputStream(file).use{output->
val buffer=data.buffer
buffer.position(0)
val bytes=ByteArray(buffer.remaining())
buffer.get(bytes)
output.write(bytes)
}
true
}catch(e:Exception){
false
}
}
}

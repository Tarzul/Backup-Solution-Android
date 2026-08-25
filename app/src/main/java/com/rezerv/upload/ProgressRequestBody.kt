import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.IOException

class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, contentLength: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val contentLength = contentLength()
        var bytesWritten = 0L

        // Оборачиваем sink, чтобы перехватывать каждый записанный чанк
        val forwardingSink = object : ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                onProgress(bytesWritten, contentLength)
            }
        }

        val bufferedSink = forwardingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush() // Важно: сбросить буфер в конце!
    }
}
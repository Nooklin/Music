Put the four model files here before building:
vocals.fp16.onnx, drums.fp16.onnx, bass.fp16.onnx, other.fp16.onnx
(Spleeter 4 stems as ONNX, 19.7 MB each, from huggingface.co/Best-Practice/spleeter-4stems-onnx, Apache-2.0;
the weights are Deezer's Spleeter, MIT). Without them the stems setting does nothing and the filters are used.

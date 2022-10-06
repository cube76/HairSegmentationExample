package id.infidea.hairsegmentationexample;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.proto.CalculatorProto;

public class FrameProcessorExtended extends FrameProcessor{

    public FrameProcessorExtended(Context context, long parentNativeContext, String graphName, String inputStream, @Nullable String outputStream) {
        super(context, parentNativeContext, graphName, inputStream, outputStream);
//        this.getGraph().startRunningGraph();
//        Log.e("cek", String.valueOf(this.getGraph()));
//        Log.e("cek3", String.valueOf(this.getGraph().getStepMode()));
//        this.getGraph().nativeGetCalculatorGraphConfig("","");
//        Log.e("cek2", this.getGraph().loadBinaryGraph(Path("")));
    }

    public FrameProcessorExtended(Context context, String graphName) {
        super(context, graphName);
    }

    public FrameProcessorExtended(CalculatorProto.CalculatorGraphConfig graphConfig) {
        super(graphConfig);
    }
}

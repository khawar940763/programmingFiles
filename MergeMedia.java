import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.*;

public class MergeMedia {
    IMediaWriter writer;
    IContainer vContainer , aContainer;
    IPacket vPacket , aPacket;
    int vStreamt , aStreamt ;
    IStreamCoder vCoder ,  aCoder;

    MergeMedia(String audio  , String video){
        writer = ToolFactory.makeWriter("ResultantOutput.flv"); // For writing in the file to make it the output file desired
        startMerge(video , "video");
        startMerge(audio , "audio");
        mergeLoop("video");
        mergeLoop("audio");
    }

    public void startMerge(String fileName , String type){
            IContainer container = IContainer.make();
            if(container.open(fileName , IContainer.Type.READ , null) < 0){
                throw new RuntimeException("Unable to open this file");
            }
            int streamt = -1;
            int numStreams = container.getNumStreams();
            IStreamCoder code = null;

            for(int i = 0 ; i < numStreams ; i++){
                IStream stream = container.getStream(i);
                IStreamCoder coder = stream.getStreamCoder();

                boolean typeCheck = false;
                if(type == "video"){
                    typeCheck = coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO;
                }else if(type == "audio"){
                    typeCheck = coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO;
                }

                if(typeCheck){
                    streamt = i;
                    code = coder;
                    break;
                }
            }
            if(streamt == -1 ){
                throw   new RuntimeException("File not found");
            }
            if(code.open() < 0 ) throw new RuntimeException("Unable to open coder");

            if(type == "video"){
                writer.addVideoStream(1 , 1 , code.getWidth() , code.getHeight());
                vCoder = code;
                vPacket = IPacket.make();
                vStreamt = streamt;
                vContainer = container;
            }else if(type == "audio"){
                writer.addAudioStream(0 , 0 , code.getChannels() , code.getSampleRate());
                aCoder = code;
                aPacket = IPacket.make();
                aStreamt = streamt;
                aContainer = container;
            }
    }

    public void mergeLoop(String type){
        if(type == "video"){
            while (vContainer.readNextPacket(vPacket) >= 0) {
                if(vPacket.getStreamIndex() == vStreamt){
                    IVideoPicture picture = IVideoPicture.make(vCoder.getPixelType() ,
                            vCoder.getWidth() , vCoder.getHeight());

                    int offset = 0;
                    while(offset < vPacket.getSize()){
                        int decoded = vCoder.decodeVideo(picture , vPacket , offset);

                        if(decoded < 0 ) throw new RuntimeException();
                        offset += decoded;

                        if(picture.isComplete()){
                            System.out.println(vCoder.getPixelType());
                            writer.encodeVideo(1 , picture);
                        }
                    }
                }
            }
        }else if(type == "audio"){
            while(aContainer.readNextPacket(aPacket) >= 0){
                if(aPacket.getStreamIndex()  == aStreamt){
                    IAudioSamples samples = IAudioSamples.make(512 , aCoder.getChannels(),
                            IAudioSamples.Format.FMT_S32);

                    int offset = 0;
                    while(offset < aPacket.getSize()){
                        int decoded = aCoder.decodeAudio(samples , aPacket , offset);

                        if(decoded < 0) throw new RuntimeException();
                        offset += decoded;

                        writer.encodeAudio(0 , samples);
                    }
                }
            }
        }
    }
}

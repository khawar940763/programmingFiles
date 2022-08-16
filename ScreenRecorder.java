import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ScreenRecorder implements  Runnable{
    JFrame frame;
    boolean isVideoThread = false;
    String videoFile = "videoGenerated.mp4";
    String audioFile = "audioGenerated.wav";
    TargetDataLine targetLine;
    Rectangle rect;
    ScreenRecorder(){
        createWindow();
    }

    public void createWindow(){
        frame = new JFrame();
        frame.setUndecorated(true);
        JRootPane root = frame.getRootPane();
        root.setWindowDecorationStyle(JRootPane.FRAME);
        root.setBorder(BorderFactory.createDashedBorder(Color.red , 3, 4 , 5 , false));
        frame.setSize(600 , 400);
        frame.setOpacity(0.5f);

        frame.setLocationRelativeTo(null);
        JButton btn = new JButton("Start Recording");
        btn.setForeground(Color.red);
        frame.setTitle("Recording Window");

        frame.add(btn  , BorderLayout.SOUTH);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        addEvents(btn);
    }

    public void addEvents(JButton btn){
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                frame.setTitle("Recording Window (Width : "+frame.getBounds().width+", Height : "+frame.getBounds().height+")");
            }
        });

        btn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(e.getActionCommand() == "Start Recording"){
                   buttonAction(e , btn);
                }else{
                    isVideoThread = false;
                    btn.setText("Start Recording");
                }
            }
        });
    }

    public void buttonAction(ActionEvent e , JButton btn){
        rect = frame.getBounds();
        rect.width = (int) Math.ceil(rect.width / 2 ) * 2;
        rect.height  = (int ) Math.ceil(rect.height /2 ) * 2;
        btn.setText("Stop Recording");
        Thread videoThread = new Thread(this);
        isVideoThread = true;
        videoThread.start();
        frame.setState(JFrame.ICONIFIED);
    }

    @Override
    public void run() {
        IMediaWriter writer = ToolFactory.makeWriter(videoFile);
        writer.addVideoStream(0  , 0 , ICodec.ID.CODEC_ID_H264 , rect.width , rect.height);
        long startTime = System.nanoTime();
        startAudio();

        while(true){
            Robot robot = null;
            try {
                robot = new Robot();
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
            BufferedImage image = robot.createScreenCapture(rect);
            BufferedImage capImage;
            if(image.getType() == BufferedImage.TYPE_3BYTE_BGR){
                capImage = image;
            }else{
                capImage = new BufferedImage(image.getWidth() , image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                capImage.getGraphics().drawImage(image , 0 , 0 ,null);
            }
            writer.encodeVideo(0 , capImage, System.nanoTime() - startTime ,TimeUnit.NANOSECONDS);
            if(isVideoThread == false){
                targetLine.stop(); // for stoping audio recording thread
                targetLine.close(); // to terminate the audio recording thread
                break;
            }
        }
        writer.close();
        IContainer container = IContainer.make();
        if(container.open(videoFile , IContainer.Type.READ , null) < 0){
            throw new RuntimeException("Can not open this file");
        }
        Thread mergeThread = new Thread(){
            public void run(){
                new MergeMedia(audioFile , videoFile);
            }
        };
        mergeThread.start();
        JOptionPane.showMessageDialog(frame , "Please wait your video and audio is being processed");
        while (mergeThread.isAlive()){
            continue;
        }
        JOptionPane.showMessageDialog(frame , "File Saved ( Size: "+ container.getFileSize()+", Duration: "+container.getDuration());
    }

    public void startAudio(){
        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED , 44100 , 16  , 2 , 4 , 44100 , false);
        DataLine.Info dataInfo = new DataLine.Info(TargetDataLine.class , audioFormat);

        if(!AudioSystem.isLineSupported(dataInfo)){
            return;
        }

        try {
            targetLine = (TargetDataLine) AudioSystem.getLine(dataInfo);
            targetLine.open();
            targetLine.start();
        }catch(LineUnavailableException e){
            return;
//            e.printStackTrace();
        }

        Thread audioThread  = new Thread(){

            @Override
            public  void run(){
                File aFile = new File(audioFile);
                AudioInputStream audioInputStream = new AudioInputStream(targetLine);
                try {
                    AudioSystem.write(audioInputStream , AudioFileFormat.Type.WAVE , aFile);
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        };
        audioThread.start();
    }

    public static void main(String[] args) {
        new ScreenRecorder();
    }

}

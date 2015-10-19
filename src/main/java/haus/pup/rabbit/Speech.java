package haus.pup.rabbit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.ivona.services.tts.IvonaSpeechCloudClient;
import com.ivona.services.tts.model.CreateSpeechRequest;
import com.ivona.services.tts.model.CreateSpeechResult;
import com.ivona.services.tts.model.Input;
import com.ivona.services.tts.model.Voice;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;

import javazoom.jl.player.Player;

public class Speech {
  private static IvonaSpeechCloudClient speechCloud = new IvonaSpeechCloudClient(
          new ClasspathPropertiesFileCredentialsProvider("IvonaCredentials.properties"));;
  private static Logger logger = LoggerFactory.getLogger("rabbit.Speech");

  String defaultVoice = "Emma";
  String cacheDir = "tts";

  public Speech() {
    init();
  }

  private void init() {
    logger.info("Initializing Speech");
    speechCloud.setEndpoint("https://tts.eu-west-1.ivonacloud.com");

    File fCache = new File(cacheDir);


    if (fCache.isDirectory()) {
      logger.debug(cacheDir + " Exists");
    }
    else {
      if (fCache.mkdir()) {
        logger.info(cacheDir + " Created");
      }
      else {
        logger.error(cacheDir + " could not be Created");
      }
    }
    for(int i=0; i<16; i++){
      for(int j=0; j<16; j++){
        String testDir = cacheDir + "/" + String.format("%01x", i & 0xf) + "/" + String.format("%01x", j & 0xf);
        fCache = new File(testDir);

        if (fCache.isDirectory()) {
          logger.debug(testDir + " Exists");
        }
        else {
          if (fCache.mkdirs()) {
            logger.info(testDir + " Created");
          }
          else {
            logger.error(testDir + " could not be Created");
          }
        }
      }
    }
    playFile("Emma", "Hello Tyr");
  }

  private void playFile(String v, String t) {
    String filename = cacheDir + "/" + getCacheFilename(v, t);

    try {
      FileInputStream fis     = new FileInputStream(new File(filename).getCanonicalPath());
      BufferedInputStream bis = new BufferedInputStream(fis);
      final Player player = new Player(bis);

      new Thread() {
        public void run() {
          try { player.play(); }
          catch (Exception e) { System.out.println(e); }
        }
      }.start();
    }
    catch (Exception e) {
      System.out.println("Problem playing file " + filename);
      System.out.println(e);
    }
  }

  /**
   * Wrapper around Ivona Example code to retrieve a file
   * @param v - voice
   * @param t - text to speak
   */
  private void getFile(String v, String t) {
    File outputFile = new File(cacheDir + "/" + getCacheFilename(v, t));

    if (!outputFile.exists()) {
      logger.info("Retrieving speech file");
      Logger ivonaLog = LoggerFactory.getLogger("rabbit.Speech.ivona");

      CreateSpeechRequest createSpeechRequest = new CreateSpeechRequest();
      Input input = new Input();
      Voice voice = new Voice();

      voice.setName(v);
      input.setData(t);

      createSpeechRequest.setInput(input);
      createSpeechRequest.setVoice(voice);
      InputStream in = null;
      FileOutputStream outputStream = null;

      try {

        CreateSpeechResult createSpeechResult = speechCloud.createSpeech(createSpeechRequest);


        ivonaLog.trace("\nSuccess sending request:");
        ivonaLog.trace(" content type:\t" + createSpeechResult.getContentType());
        ivonaLog.trace(" request id:\t" + createSpeechResult.getTtsRequestId());
        ivonaLog.trace(" request chars:\t" + createSpeechResult.getTtsRequestCharacters());
        ivonaLog.trace(" request units:\t" + createSpeechResult.getTtsRequestUnits());

        ivonaLog.trace("\nStarting to retrieve audio stream:");

        in = createSpeechResult.getBody();
        outputStream = new FileOutputStream(outputFile);

        byte[] buffer = new byte[2 * 1024];
        int readBytes;

        while ((readBytes = in.read(buffer)) > 0) {
          outputStream.write(buffer, 0, readBytes);
        }

        ivonaLog.info("\nFile saved: " + outputFile.getPath());

      } catch (FileNotFoundException e) {
        ivonaLog.error("File Not Found exception Occurred. See Trace.");
        e.printStackTrace();
      } catch (IOException e) {
        ivonaLog.error("IO exception Occurred. See Trace.");
        e.printStackTrace();
      } finally {
        try {
          if (in != null) {
            in.close();
          }
          if (outputStream != null) {
            outputStream.close();
          }
          logger.info("Speech file saved: " + outputFile.getPath());
        } catch (IOException e) {
          ivonaLog.error("IO exception Occurred. See Trace.");
          e.printStackTrace();
        }
      }
    } else {
      logger.info("Speech file exists in cache");
    }
  }

  /**
   * Build folder path and filename based on MD5 of voice and Text
   * @param v - Voice
   * @param t - Text
   * @return Cache Filepath
   */
  protected String getCacheFilename(String v, String t) {
    String hash = getMD5("Ivona" + v + t);

    String folder1 = hash.substring(0,1);
    String folder2 = hash.substring(1,2);
    String filename = hash.substring(2);

    return folder1 + "/" + folder2 + "/" + filename + ".mp3";
  }

  protected String getMD5(String s) {
    // Safely Create MD5 Message Digest
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      logger.error("No Such Algorithm exception Occurred. See Trace.");
      e.printStackTrace();
    }

    md.update(s.getBytes());
    byte[] digest = md.digest();
    StringBuilder sb = new StringBuilder();
    for (byte b : digest) {
      sb.append(String.format("%02x", b & 0xff));
    }

    return sb.toString();
  }
}

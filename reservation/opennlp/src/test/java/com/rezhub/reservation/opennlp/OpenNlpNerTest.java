package com.rezhub.reservation.opennlp;

import opennlp.tools.namefind.*;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * From <a href="https://www.tutorialspoint.com/opennlp/opennlp_named_entity_recognition.htm">NER</a>
 */
public class OpenNlpNerTest {

    private static final File resourcesDir;
    private static final File targetDir;

    static {
        try {
            resourcesDir = new File(assertAndReturnCanonicalPath("src/test/resources/"));
            targetDir = new File(assertAndReturnCanonicalPath("target"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    record NerOut(Span span, String value) {}

    public static String assertAndReturnCanonicalPath(String path) throws IOException {
        File file = new File(path);
        assertTrue(file.exists());
        return file.getCanonicalPath();
    }

    @NotNull
    private static List<NerOut> getNerOuts(String sentence, File enTokenFile, File nerFile) throws IOException {
        String[] tokens = tokenize(sentence, enTokenFile);
        Span[] nameSpans = findSpans(nerFile, tokens);
        return nerResult(tokens, nameSpans);
    }

    @NotNull
    private static List<NerOut> nerResult(String[] tokens, Span[] nameSpans) {
        return Arrays.stream(nameSpans).map(s -> new NerOut(s, tokens[s.getStart()])).collect(Collectors.toList());
    }

    private static Span[] findSpans(File nerFile, String[] tokens) throws IOException {
        TokenNameFinderModel model = new TokenNameFinderModel(new FileInputStream(nerFile));
        return new NameFinderME(model).find(tokens);
    }

    private static String[] tokenize(String sentence, File enTokenFile) throws IOException {
        TokenizerModel tokenModel = new TokenizerModel(new FileInputStream(enTokenFile));
        return new TokenizerME(tokenModel).tokenize(sentence);
    }

    //code taken from Medium article https://medium.com/analytics-vidhya/named-entity-recognition-in-java-using-open-nlp-4dc7cfc629b4
    //but doesn't work
    private static File createAndSaveModel() throws Exception {
        InputStreamFactory in = new MarkableFileInputStreamFactory(new File(resourcesDir, "training_dataset.txt"));
        ObjectStream<NameSample> sampleStream = new NameSampleDataStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
        TrainingParameters params = new TrainingParameters(Map.of(
                TrainingParameters.ITERATIONS_PARAM, 70,
                TrainingParameters.CUTOFF_PARAM, 1,
                TrainingParameters.ALGORITHM_PARAM, "MAXENT"
        ));

        // training the model using TokenNameFinderModel class
        TokenNameFinderModel nameFinderModel = NameFinderME.train("en", null, sampleStream,
                params, TokenNameFinderFactory.create(null, null, Collections.emptyMap(), new BioCodec()));

        // saving the model to "ner-custom-model.bin" file
        File output = new File(targetDir, "ner-custom-model.bin");
        nameFinderModel.serialize(new FileOutputStream(output));
        return output;
    }

    @Test
    public void simpleNamedEntityRecognition() throws Exception {
        String sentence = "My goodness, have you logged on the the network on June 30th, 2023 at 13:00?";
        File enToken = new File(resourcesDir, "opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin");
        File nerFile = createAndSaveModel();

        // Tokenize sentences
        String[] tokens = tokenize(sentence, enToken);
        Span[] nameSpans = findSpans(nerFile, tokens);
        List<NerOut> ners = nerResult(tokens, nameSpans);
        ners.forEach(n -> System.out.println(n.span + " " + n.value));

        System.out.println("testing the model and printing the types it found in the input sentence:");
        for (Span name : nameSpans) {
            StringBuilder entity = new StringBuilder();
            System.out.println(name);
            for (int i = name.getStart(); i < name.getEnd(); i++) {
                entity.append(tokens[i]).append(" ");
            }
            System.out.println(name.getType() + " : " + entity + "\t [probability=" + name.getProb() + "]");
        }
    }

    @Test
    public void personFinder() throws Exception {
        String sentence = "Mike is senior programming manager and John is a clerk both are working at Tutorialspoint";
        File enTokenFile = new File(resourcesDir, "en-token.bin");
        File nerFile = new File(resourcesDir, "en-ner-person.bin");

        List<NerOut> ners = getNerOuts(sentence, enTokenFile, nerFile);
        ners.forEach(n -> System.out.println(n.span + " " + n.value));
    }

    @Test
    public void locationFinder() throws Exception {
        String sentence = "Tutorialspoint is located in Hyderabad";
        File enTokenFile = new File(resourcesDir, "en-token.bin");
        File nerFile = new File(resourcesDir, "en-ner-location.bin");

        List<NerOut> ners = getNerOuts(sentence, enTokenFile, nerFile);
        ners.forEach(n -> System.out.println(n.span + " " + n.value));
    }

    @Test
    public void timeFinder() throws Exception {
        String sentence = "Book me a court at 4pm tomorrow";
        File enTokenFile = new File(resourcesDir, "en-token.bin");
        File nerFile = new File(resourcesDir, "en-ner-time.bin");

        List<NerOut> ners = getNerOuts(sentence, enTokenFile, nerFile);
        ners.forEach(n -> System.out.println(n.span + " " + n.value));
    }

    @Test
    public void dateFinder() throws Exception {
        String sentence = "Due to system maintenance, Certain account related features on Net Banking would not be " +
                "available till Monday 6th September at 17:00. Credit Card Enquiry, Demat, and Debit Card details " +
                "would continue to be available. We regret the inconvenience caused";
        File enTokenFile = new File(resourcesDir, "en-token.bin");
        File nerFile = new File(resourcesDir, "en-ner-date.bin");

        List<NerOut> ners = getNerOuts(sentence, enTokenFile, nerFile);
        ners.forEach(n -> System.out.println(n.span + ": " + n.value));
    }
}

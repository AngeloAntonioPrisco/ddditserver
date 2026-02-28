package it.unisa.ddditserver.subsystems.ai.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import it.unisa.ddditserver.subsystems.ai.TagClassificationModelConfig;
import it.unisa.ddditserver.subsystems.ai.exceptions.TagClassificationException;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import jakarta.annotation.PostConstruct;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.io.FileOutputStream;
import java.util.Properties;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;

@Service
public class TagClassificationServiceImpl implements TagClassificationService {
    private final TagClassificationModelConfig config;
    private final List<File> models;

    @Autowired
    public TagClassificationServiceImpl(TagClassificationModelConfig config) {
        this.models = new ArrayList<>();
        this.config = config;
    }

    @PostConstruct
    public void init() {
        getOnnxModelsInFolder();
    }

    private static void sendEmail(String from, String appPassword, String to, String subject, String body) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, appPassword);
            }
        });

        Message message;

        try {
            message = new MimeMessage(session);

                message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
        } catch (MessagingException e) {
            throw new TagClassificationException("Error sending alert message during classification");
        }
    }

    public void getOnnxModelsInFolder() {
        models.clear();

        String folderPath = config.getModelsFolderPath();

        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".onnx"));

        if (files != null) {
            models.addAll(Arrays.asList(files));
        }
    }

    private ArrayList<String> predictAllModels(float[] inputFeatures) {
        ArrayList<String> results = new ArrayList<>();

        for (File modelFile : models) {
            long[] output;

            try {
                output = runOnnxModel(inputFeatures, modelFile);
            } catch (TagClassificationException e) {
                throw e;
            } catch (Exception e) {
                throw new TagClassificationException("Error predicting for " + modelFile + " model");
            }

            if (output[0] == 1) {
                results.add(modelFile.getName().split("_")[2].split("\\.")[0]);
            }
        }

        return results;
    }

    private long[] runOnnxModel(float[] inputFeatures, File modelFile) {
        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
             OrtSession session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions())) {

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, new float[][] { inputFeatures });
            String inputName = session.getInputNames().iterator().next();

            // CPU & RAM start
            Runtime runtime = Runtime.getRuntime();
            long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long cpuTimeBefore = threadBean.getCurrentThreadCpuTime();

            // Predictions time start
            long startTime = System.nanoTime();
            OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor));
            long endTime = System.nanoTime();

            // CPU & RAM end
            long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = usedMemoryAfter - usedMemoryBefore;
            long cpuTime = threadBean.getCurrentThreadCpuTime() - cpuTimeBefore;
            long durationMs = (endTime - startTime) / 1_000_000;

            // Thresholds
            boolean slowInference = durationMs >= 5000;
            boolean tooMuchMemory = memoryUsed >= 700L * 1024 * 1024;
            boolean highCpu = cpuTime >= 2_000_000_000L;

            if (slowInference || tooMuchMemory || highCpu) {
                String from = config.getFromEmail();
                String appPassword = config.getAppPassword();
                String to = config.getToEmail();
                String subject = "[ALERT] Dddit AI module performance risk";
                String body = "ATTENTION - Dddit AI module performance issue detected:\n\n"
                        + "- Inference time: " + durationMs + " ms\n"
                        + "- Memory used: " + (memoryUsed / (1024 * 1024)) + " MB\n"
                        + "- CPU time: " + (cpuTime / 1_000_000) + " ms";

                sendEmail(from, appPassword, to, subject, body);
            }

            Object rawOutput = result.get(0).getValue();

            long[] output;
            if (rawOutput instanceof long[]) {
                output = (long[]) rawOutput;
            } else if (rawOutput instanceof long[][]) {
                output = ((long[][]) rawOutput)[0];
            } else {
                throw new TagClassificationException("Unexpected ONNX output type during classification");
            }

            return output;
        } catch (TagClassificationException e) {
            throw e;
        } catch (Exception e) {
            throw new TagClassificationException("Error during ONNX model running");
        }
    }

    private Map<String, Double> extractFbxFeatures(File fbxFile) {
        AIScene scene = Assimp.aiImportFile(
                fbxFile.getAbsolutePath(),
                Assimp.aiProcess_Triangulate | Assimp.aiProcess_JoinIdenticalVertices
        );

        if (scene == null) {
            throw new TagClassificationException("Error during fbx file reading");
        }

        int vertexCount = 0;
        int faceCount = 0;
        int materialCount = scene.mNumMaterials();
        int textureCount = 0;
        int animationCount = scene.mNumAnimations();

        PointerBuffer meshes = scene.mMeshes();
        for (int i = 0; i < scene.mNumMeshes(); i++) {
            AIMesh mesh = AIMesh.create(meshes != null ? meshes.get(i) : 0);
            vertexCount += mesh.mNumVertices();
            faceCount += mesh.mNumFaces();
        }

        for (int i = 0; i < materialCount; i++) {
            AIMaterial mat = AIMaterial.create(scene.mMaterials().get(i));
            textureCount += Assimp.aiGetMaterialTextureCount(mat, Assimp.aiTextureType_DIFFUSE);
        }

        double textureRichness = (double) textureCount / (materialCount + 1);

        Map<String, Double> features = new HashMap<>();
        features.put("vertex_count", (double) vertexCount);
        features.put("face_count", (double) faceCount);
        features.put("material_count", (double) materialCount);
        features.put("texture_count", (double) textureCount);
        features.put("animation_count", (double) animationCount);
        features.put("texture_richness", textureRichness);
        // At the moment this feature can't be calculated, a better idea is to account this problem in AI module documentation
        features.put("vertex_count_scaled", 0.0);
        // At the moment this feature can't be calculated, a better idea is to account this problem in AI module documentation
        features.put("material_count_scaled", 0.0);

        return features;
    }

    public ArrayList<String> classify(VersionDTO versionDTO) {
        MultipartFile mesh = versionDTO.getMesh();

        byte[] bytes = null;

        try {
            // A copy of the file is necessary because Tomcat can throw an exception
            bytes = mesh.getBytes();
            File tempFile = null;
            tempFile = File.createTempFile("mesh_", ".fbx");

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(bytes);
            }

            Map<String, Double> features = extractFbxFeatures(tempFile);

            if (!tempFile.delete()) {
                throw new TagClassificationException("Error during deletion of multipart copy");
            }

            float[] inputFeatures = new float[] {
                    features.get("vertex_count").floatValue(),
                    features.get("face_count").floatValue(),
                    features.get("material_count").floatValue(),
                    features.get("texture_count").floatValue(),
                    features.get("animation_count").floatValue(),
                    features.get("texture_richness").floatValue(),
                    features.get("vertex_count_scaled").floatValue(),
                    features.get("material_count_scaled").floatValue()
            };

            return predictAllModels(inputFeatures);
        } catch (TagClassificationException e) {
            throw e;
        } catch (Exception e) {
            throw new TagClassificationException("Error during classification");
        }
    }
}

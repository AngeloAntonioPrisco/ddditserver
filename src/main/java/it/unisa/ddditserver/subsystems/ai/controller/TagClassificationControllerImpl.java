package it.unisa.ddditserver.subsystems.ai.controller;

import it.unisa.ddditserver.subsystems.ai.service.TagClassificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/ai")
public class TagClassificationControllerImpl implements TagClassificationController {
    @Autowired
    private TagClassificationService tagClassificationService;

    @Override
    @GetMapping("/reload")
    public ResponseEntity<?> reloadModel() {
        tagClassificationService.getOnnxModelsInFolder();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Successfully reloaded model");

        return ResponseEntity.ok(response);
    }
}

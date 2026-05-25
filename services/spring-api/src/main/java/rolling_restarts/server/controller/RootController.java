package rolling_restarts.server.controller;

import org.openapitools.api.DummyApi;
import org.openapitools.model.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController implements DummyApi {

    @GetMapping("/")
    public ResponseEntity<Message> root() {
        Message message = new Message();
        message.setMessage("Hello, World!");
        return ResponseEntity.ok(message);
    }

    @GetMapping("/test")
    public ResponseEntity<Message> test() {
        Message message = new Message();
        message.setMessage("Hello, World!\nTest!");
        return ResponseEntity.ok(message);
    }

    @Override
    public ResponseEntity<Message> dummy() {
        Message message = new Message();
        message.setMessage("Dummy response");
        return ResponseEntity.ok(message);
    }
}

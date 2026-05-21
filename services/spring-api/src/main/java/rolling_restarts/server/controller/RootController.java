package rolling_restarts.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Message> root() {
        return ResponseEntity.ok(new Message("Hello, World!"));
    }

    public static class Message {
        private String message;

        public Message() {}

        public Message(String message) { this.message = message; }

        public String getMessage() { return message; }

        public void setMessage(String message) { this.message = message; }
    }
}

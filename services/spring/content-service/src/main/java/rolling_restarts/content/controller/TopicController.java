package rolling_restarts.content.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import rolling_restarts.content.model.Topic;
import rolling_restarts.content.repository.TopicRepository;

@RestController
@RequestMapping("/topics")
@Tag(name = "Topics", description = "Content topic categories")
public class TopicController {

	private final TopicRepository topicRepository;

	public TopicController(TopicRepository topicRepository) {
		this.topicRepository = topicRepository;
	}

	@GetMapping
	@Operation(summary = "List all topics")
	public List<Topic> list() {
		return topicRepository.findAll();
	}
}

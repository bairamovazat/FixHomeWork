package ru.ivmiit.controlers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.ivmiit.forms.AddChatForm;
import ru.ivmiit.forms.AddMemberForm;
import ru.ivmiit.forms.SendMessageForm;
import ru.ivmiit.models.Chat;
import ru.ivmiit.models.File;
import ru.ivmiit.models.Message;
import ru.ivmiit.models.User;
import ru.ivmiit.repositories.ChatsRepository;
import ru.ivmiit.repositories.MessagesRepository;
import ru.ivmiit.repositories.UsersRepository;
import ru.ivmiit.services.AuthenticationService;
import ru.ivmiit.services.ChatService;
import ru.ivmiit.services.FileService;
import ru.ivmiit.transfer.ChatDto;
import ru.ivmiit.transfer.MessageDto;
import ru.ivmiit.transfer.UserDto;

import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static ru.ivmiit.transfer.MessageDto.from;

@Controller
public class ChatController {

    @Autowired
    private AuthenticationService service;

    @Autowired
    private FileService fileService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ChatsRepository chatsRepository;

    @Autowired
    private MessagesRepository messagesRepository;

    @Autowired
    private ChatService chatService;

    @GetMapping("/chats")
    public String getMainAdminPage(Authentication authentication, @ModelAttribute("model") ModelMap model) {
        User user = service.getUserByAuthentication(authentication);
        List<Chat> chats = chatsRepository.findByMembersContains(user);
        chatService.keepFirstMessageInChats(chats);
        model.addAttribute("chats", ChatDto.from(chats));
        model.addAttribute("user", UserDto.from(user));
        return "chat/chats_page";
    }

    @GetMapping("/chat/{chatId}")
    public String getChatPage(@PathVariable("chatId") Long chatId,
                              Authentication authentication,
                              @ModelAttribute("model") ModelMap model,
                              @RequestParam(value = "error", required=false) String error){
        model.addAttribute("error", error);
        User user = service.getUserByAuthentication(authentication);
        Optional<Chat> chat = chatsRepository.findByMembersContainsAndId(user, chatId);
        if (!chat.isPresent()) {
            return "redirect:/chats?error";
        }
        model.addAttribute("chat", ChatDto.from(chat.get()));
        return "chat/chat_page";
    }

    @PostMapping("/chats/add")
    public String addChat(@ModelAttribute("userForm") AddChatForm addChatForm,
                          Authentication authentication,
                          @ModelAttribute("model") ModelMap model) {
        User user = service.getUserByAuthentication(authentication);
        Chat chat = Chat.builder()
                .name(addChatForm.getName())
                .members(Collections.singletonList(user))
                .owner(user)
                .build();
        chatsRepository.save(chat);
        return "redirect:/chats";
    }


    @PostMapping("/chats/add/member")
    public String addMember(@ModelAttribute("userForm") AddMemberForm addMemberForm,
                            Authentication authentication,
                            @ModelAttribute("model") ModelMap model,
                            RedirectAttributes redirectAttributes) {
        User user = service.getUserByAuthentication(authentication);
        Optional<Chat> chat = chatsRepository.findByMembersContainsAndId(user, addMemberForm.getChatId());
        Optional<User> newMember = usersRepository.findOneByLogin(addMemberForm.getUserName());

        if (!chat.isPresent()) {
            redirectAttributes.addAttribute("error", "Чат не найден");
            return "redirect:/chats";
        } else if(chat.get().getMembers().contains(newMember)){
            redirectAttributes.addAttribute("error", "Пользователь уже добавлен");
            return "redirect:/chat/" + addMemberForm.getChatId();
        } if (!newMember.isPresent()) {
            redirectAttributes.addAttribute("error", "Неверный логин пользователя");
            return "redirect:/chat/" + addMemberForm.getChatId();
        } else {
            chatService.addMemberToChat(newMember.get(), chat.get());
            return "redirect:/chat/" + addMemberForm.getChatId();
        }
    }


    @GetMapping("/chat/{chatId}/updates/")
    @ResponseBody
    public List<MessageDto> longPoolMessageUpdate(@PathVariable("chatId") Long chatId, @RequestParam("lastMessageId") Long lastMessageId,
                                                  Authentication authentication, @ModelAttribute("model") ModelMap model) {
        User user = service.getUserByAuthentication(authentication);
        Optional<Chat> chat = chatsRepository.findByMembersContainsAndId(user, chatId);
        if (!chat.isPresent()) {
            throw new IllegalArgumentException("bad chat id");
        }
        //если требуются все сообщени
        if(lastMessageId == -1){
            List<Message> messages = chatService.waitNewMessages(chat.get(), Message.builder().id(-1L).build());
            return from(messages);
        }

        Optional<Message> message = messagesRepository.getMessageById(lastMessageId);
        if (!message.isPresent()) {
            throw new IllegalArgumentException("bad message id");
        }
        List<Message> messages = chatService.waitNewMessages(chat.get(), message.get());
        return from(messages);
    }

    @PostMapping("/chat/send/")
    @ResponseBody
    public ResponseEntity<String> sendMessage(@RequestBody SendMessageForm sendMessageForm,
                                      Authentication authentication) throws IllegalArgumentException{
        User user = service.getUserByAuthentication(authentication);
        Optional<Chat> chat = chatsRepository.findByMembersContainsAndId(user, sendMessageForm.getChatId());
        if (!chat.isPresent()) {
            throw new IllegalArgumentException("bad chat id");
        }else if(sendMessageForm.getMessage().isEmpty()){
            throw new IllegalArgumentException("message is empty");
        }

        Message message = Message.builder()
                .user(user)
                .text(sendMessageForm.getMessage())
                .sendDate(new Date())
                .isRead(false)
                .chat(chat.get())
                .build();

        messagesRepository.save(message);

        return ResponseEntity.ok("{\"success\":\"success\"}");
    }

    @PostMapping("/chat/send-file/")
    @ResponseBody
    public ResponseEntity<String> sendFile(@RequestParam("chatId") Long chatId,
                                           @RequestParam("message") String message,
                                           @RequestParam("file") MultipartFile multipartFile,
                                           Authentication authentication) throws IllegalArgumentException{
        User user = service.getUserByAuthentication(authentication);

        Optional<Chat> chat = chatsRepository.findByMembersContainsAndId(user, chatId);
        if (!chat.isPresent()) {
            throw new IllegalArgumentException("bad chat id");
        }else if(message.isEmpty()){
            throw new IllegalArgumentException("message is empty");
        }

        File file = fileService.saveFile(multipartFile);

        Message fileMessage = Message.builder()
                .user(user)
                .text("<a href=\'file/" + file.getUrl() + "\'> Файл: " + file.getName() + "</a>")
                .sendDate(new Date())
                .isRead(false)
                .chat(chat.get())
                .build();

        messagesRepository.save(fileMessage);

        return ResponseEntity.ok("{\"success\":\"success\"}");
    }

    @GetMapping("/chat/file/{file-name:.+}")
    public void getFile(@PathVariable("file-name") String fileName,
                        HttpServletResponse response){
        fileService.writeFileToResponse(fileName, response);
    }

}

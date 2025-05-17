//  // Constants for API and WebSocket endpoints
//     const API_BASE_URL = 'http://localhost:8080/api';
//     const WS_ENDPOINT = 'http://localhost:8080/ws';

//     // Application state variables
//     let jwtToken = null;
//     let loggedInUser = { username: null, id: null, avatarUrl: null, bio: null }; // Store more user info
//     let currentServerId = null;
//     let currentChannelId = null;
//     let currentDmChatId = null;
//     let currentChatType = null; // 'channel' or 'dm'
//     let stompClient = null;
//     let activeSubscription = null;
//     const messageHistory = new Map(); // Store messages by chat ID to avoid re-fetching/re-rendering
//     let uploadedFiles = []; // For file attachments
//     let replyingToMessage = null; // For message replies

//     // DOM Element references
//     const $ = (selector) => document.querySelector(selector);
//     const $$ = (selector) => document.querySelectorAll(selector);

//     const loginScreen = $('#loginScreen');
//     const appScreen = $('#appScreen');
//     const loginUsernameInput = $('#loginUsername');
//     const loginPasswordInput = $('#loginPassword');
//     const loginButton = $('#loginButton');
//     const loginStatus = $('#loginStatus');

//     const serverBar = $('#serverBar');
//     const userProfileBtn = $('#userProfileBtn');
//     const channelListBar = $('#channelListBar');
//     const serverNameHeader = $('#serverNameHeader h2');
//     const channelsAndDmsList = $('#channelsAndDmsList');

//     const chatView = $('#chatView');
//     const chatHeader = $('#chatHeader');
//     const chatName = $('#chatName');
//     const messageLog = $('#messageLog');
//     const messageInput = $('#messageInput');
//     const sendMessageButton = $('#sendMessageButton');
//     const attachFileButton = $('#attachFileButton');
//     const fileUploadInput = $('#fileUploadInput');
//     const fileUploadPreviewContainer = $('#fileUploadPreviewContainer');
//     const replyingToBar = $('#replyingToBar');
//     const replyingToUser = $('#replyingToUser');
//     const replyingToText = $('#replyingToText');
//     const cancelReplyBtn = $('#cancelReplyBtn');

//     const profileModalContainer = $('#profileModalContainer');
//     const profileModalAvatar = $('#profileModalAvatar');
//     const changeAvatarButton = $('#changeAvatarButton');
//     const avatarFileInput = $('#avatarFileInput');
//     const profileDisplayNameInput = $('#profileDisplayName');
//     const profileBioInput = $('#profileBio');
//     const saveProfileButton = $('#saveProfileButton');
//     const cancelProfileButton = $('#cancelProfileButton');
//     const profileModalStatus = $('#profileModalStatus');


//     // --- UTILITY FUNCTIONS ---
//     function showToast(message, type = 'info') {
//         const toast = document.createElement('div');
//         toast.className = 'toast-notification';
//         toast.textContent = message;
//         if (type === 'error') toast.style.backgroundColor = 'var(--danger)';
//         if (type === 'success') toast.style.backgroundColor = 'var(--success)';
//         document.body.appendChild(toast);
//         toast.addEventListener('animationend', () => toast.remove());
//     }

//     function createAvatarPlaceholder(name) {
//         return name ? name.charAt(0).toUpperCase() : '?';
//     }

//     function updateUserAvatarDisplay(element, username, avatarUrl) {
//         if (avatarUrl) {
//             element.innerHTML = `<img src="${avatarUrl}" alt="${username}" class="w-full h-full object-cover rounded-full">`;
//         } else {
//             element.textContent = createAvatarPlaceholder(username);
//             // Simple hashing for a background color based on username
//             let hash = 0;
//             for (let i = 0; i < (username || "").length; i++) {
//                 hash = username.charCodeAt(i) + ((hash << 5) - hash);
//             }
//             const color = `hsl(${hash % 360}, 50%, 60%)`;
//             element.style.backgroundColor = color;
//             element.innerHTML = `<span>${createAvatarPlaceholder(username)}</span>`;
//         }
//     }


//     // --- AUTHENTICATION ---
//     loginButton.addEventListener('click', async () => {
//         const username = loginUsernameInput.value.trim();
//         const password = loginPasswordInput.value.trim();
//         if (!username || !password) {
//             loginStatus.textContent = 'Username and password are required.';
//             return;
//         }
//         loginStatus.textContent = 'Logging in...';
//         try {
//             const response = await fetch(`${API_BASE_URL}/auth/login`, {
//                 method: 'POST',
//                 headers: { 'Content-Type': 'application/json' },
//                 body: JSON.stringify({ username, password }),
//             });
//             if (!response.ok) {
//                 const errorText = await response.text();
//                 throw new Error(errorText || `HTTP error! Status: ${response.status}`);
//             }
//             jwtToken = await response.text();
//             loggedInUser.username = username;
//             // Fetch user details (including ID and potential avatar) after login
//             // This assumes you have a /api/users/me endpoint or similar
//             // For now, we'll just use the username
//             loginStatus.textContent = '';
//             loginScreen.classList.add('hidden');
//             appScreen.classList.remove('hidden');
//             updateUserAvatarDisplay(userProfileBtn, loggedInUser.username, loggedInUser.avatarUrl);
//             await loadServersAndDMs();
//             showToast(`Welcome back, ${loggedInUser.username}!`, 'success');
//         } catch (error) {
//             loginStatus.textContent = `Login failed: ${error.message}`;
//             console.error('Login error:', error);
//         }
//     });

//     // --- SERVER AND DM LOADING & RENDERING ---
//     async function loadServersAndDMs() {
//         if (!jwtToken) return;
//         try {
//             // Fetch Servers
//             const serversResponse = await fetch(`${API_BASE_URL}/servers`, {
//                 headers: { 'Authorization': `Bearer ${jwtToken}` },
//             });
//             if (!serversResponse.ok) throw new Error(`Failed to fetch servers: ${serversResponse.status}`);
//             const serversData = await serversResponse.json();
//             renderServerList(serversData);

//             // Fetch DMs
//             const dmsResponse = await fetch(`${API_BASE_URL}/dm-chats`, {
//                 headers: { 'Authorization': `Bearer ${jwtToken}` },
//             });
//             if (!dmsResponse.ok) throw new Error(`Failed to fetch DMs: ${dmsResponse.status}`);
//             const dmsData = await dmsResponse.json();
//             renderDmList(dmsData);

//         } catch (error) {
//             console.error('Error loading servers/DMs:', error);
//             showToast(`Error loading data: ${error.message}`, 'error');
//         }
//     }

//     function renderServerList(servers) {
//         serverBar.innerHTML = ''; // Clear existing
//         servers.forEach(server => {
//             const serverIcon = document.createElement('div');
//             serverIcon.className = 'server-icon';
//             serverIcon.title = server.name;
//             // For now, using first letter. Replace with server.iconUrl if available
//             updateUserAvatarDisplay(serverIcon, server.name, null /* server.iconUrl */);
//             serverIcon.addEventListener('click', () => selectServer(server));
//             serverBar.appendChild(serverIcon);
//         });
//         // Add "Create Server" button
//         const addServerIcon = document.createElement('div');
//         addServerIcon.className = 'server-icon';
//         addServerIcon.textContent = '+';
//         addServerIcon.title = 'Create New Server';
//         addServerIcon.addEventListener('click', handleCreateServerPrompt);
//         serverBar.appendChild(addServerIcon);
        
//         serverBar.appendChild($('userProfileIconContainer')); // Re-append user profile button
//         updateUserAvatarDisplay(userProfileBtn, loggedInUser.username, loggedInUser.avatarUrl);
//     }

//     function renderDmList(dms) {
//         // Assuming DMs are added to the same list as channels for now
//         // You might want a separate section or different styling
//         const dmHeader = document.createElement('h3');
//         dmHeader.className = 'px-2 pt-4 pb-1 text-xs font-semibold uppercase text-[var(--text-muted)]';
//         dmHeader.textContent = 'Direct Messages';
//         channelsAndDmsList.appendChild(dmHeader);

//         dms.forEach(dm => {
//             const dmName = dm.chatType === 'GROUP_DIRECT_MESSAGE' ? dm.name : (dm.participants.find(p => p.username !== loggedInUser.username)?.username || 'DM');
//             const dmItem = document.createElement('div');
//             dmItem.className = 'channel-item';
//             dmItem.innerHTML = `<span class="avatar w-6 h-6 rounded-full bg-gray-500 flex items-center justify-center text-xs">${createAvatarPlaceholder(dmName)}</span> <span class="truncate">${dmName}</span>`;
//             dmItem.addEventListener('click', () => selectDmChat(dm));
//             channelsAndDmsList.appendChild(dmItem);
//         });
//     }
    
//     async function handleCreateServerPrompt() {
//         const serverName = prompt("Enter a name for your new server:");
//         if (serverName && serverName.trim() !== "") {
//             try {
//                 const response = await fetch(`${API_BASE_URL}/servers`, {
//                     method: 'POST',
//                     headers: { 'Authorization': `Bearer ${jwtToken}`, 'Content-Type': 'application/json' },
//                     body: JSON.stringify({ name: serverName.trim() })
//                 });
//                 if (!response.ok) {
//                     const error = await response.json();
//                     throw new Error(error.message || `Server creation failed: ${response.status}`);
//                 }
//                 showToast(`Server "${serverName}" created!`, 'success');
//                 await loadServersAndDMs(); // Refresh server list
//             } catch (error) {
//                 showToast(error.message, 'error');
//                 console.error("Create server error:", error);
//             }
//         }
//     }


//     function selectServer(server) {
//         currentServerId = server.server_id; // Assuming your DTO uses server_id
//         serverNameHeader.textContent = server.name;
//         channelsAndDmsList.innerHTML = ''; // Clear previous channels/DMs
//         renderChannelList(server.channels);
//         // Optionally re-render DMs if you want them under server context, or keep them separate
//         // renderDmList(loadedDms); // if you have a global `loadedDms` variable
        
//         // De-select any active channel/DM
//         chatName.textContent = `# select-a-channel`;
//         messageLog.innerHTML = '';
//         disconnectWebSocket();
//         currentChannelId = null;
//         currentDmChatId = null;
//         currentChatType = null;
//         sendMessageButton.disabled = true;
//     }

//     function renderChannelList(channels) {
//          const channelHeader = document.createElement('h3');
//         channelHeader.className = 'px-2 pt-2 pb-1 text-xs font-semibold uppercase text-[var(--text-muted)]';
//         channelHeader.textContent = 'Text Channels';
//         channelsAndDmsList.appendChild(channelHeader);

//         channels.forEach(channel => {
//             const channelItem = document.createElement('div');
//             channelItem.className = 'channel-item';
//             channelItem.innerHTML = `<span class="icon">#</span> <span class="truncate">${channel.name}</span>`;
//             channelItem.addEventListener('click', () => selectChannel(channel));
//             channelsAndDmsList.appendChild(channelItem);
//         });
//     }

//     function selectChannel(channel) {
//         console.log("Selected channel:", channel);
//         currentChannelId = channel.id;
//         currentDmChatId = null;
//         currentChatType = 'channel';
//         chatName.textContent = `# ${channel.name}`;
//         messageInput.placeholder = `Message #${channel.name}`;
//         setActiveChannelItem(channel.id);
//         loadAndDisplayMessages();
//     }

//     function selectDmChat(dm) {
//         console.log("Selected DM:", dm);
//         currentDmChatId = dm.id;
//         currentChannelId = null;
//         currentChatType = 'dm';
//         const dmName = dm.chatType === 'GROUP_DIRECT_MESSAGE' ? dm.name : (dm.participants.find(p => p.username !== loggedInUser.username)?.username || 'DM');
//         chatName.textContent = `@ ${dmName}`;
//         messageInput.placeholder = `Message @${dmName}`;
//         setActiveChannelItem(dm.id, true); // true for DM
//         loadAndDisplayMessages();
//     }
    
//     function setActiveChannelItem(id, isDm = false) {
//         $$('#channelsAndDmsList .channel-item').forEach(item => item.classList.remove('active'));
//         const activeItem = Array.from($$('#channelsAndDmsList .channel-item')).find(item => {
//             // This needs a robust way to identify items, e.g., data-id attribute
//             // For now, it might not work perfectly for DMs vs Channels if names clash
//             return item.textContent.includes(isDm ? `@` : `#`) && item.textContent.includes(id); // Simplified
//         });
//         if(activeItem) activeItem.classList.add('active');
//     }


//     // --- MESSAGE HANDLING & WEBSOCKETS ---
//     async function loadAndDisplayMessages() {
//         if (!currentChatType || (!currentChannelId && !currentDmChatId)) return;
//         messageLog.innerHTML = '<p class="text-center text-[var(--text-muted)]">Loading messages...</p>';
//         seenMessageIds.clear();
//         replyingToMessage = null;
//         updateReplyBar();

//         const chatId = currentChatType === 'channel' ? currentChannelId : currentDmChatId;
//         const cacheKey = `${currentChatType}-${chatId}`;

//         // For simplicity, always fetch, but ideally use cache
//         // if (messageHistory.has(cacheKey)) {
//         //     renderMessages(messageHistory.get(cacheKey));
//         //     connectWebSocket();
//         //     return;
//         // }

//         try {
//             const endpoint = currentChatType === 'channel' ?
//                 `${API_BASE_URL}/channels/${currentChannelId}/messages?page=0&size=50` :
//                 `${API_BASE_URL}/dm-chats/${currentDmChatId}/messages?page=0&size=50`;

//             const response = await fetch(endpoint, { headers: { 'Authorization': `Bearer ${jwtToken}` } });
//             if (!response.ok) throw new Error(`Failed to fetch messages: ${response.status}`);
//             const page = await response.json();
//             const messages = page.content || [];
//             messages.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)); // Ensure chronological
            
//             messageHistory.set(cacheKey, messages);
//             renderMessages(messages);
//             connectWebSocket();

//         } catch (error) {
//             console.error('Error loading messages:', error);
//             messageLog.innerHTML = `<p class="text-center text-[var(--danger)]">Error loading messages: ${error.message}</p>`;
//             disconnectWebSocket();
//         }
//     }
    
//     function renderMessages(messages) {
//         messageLog.innerHTML = ''; // Clear loading/error message
//         messages.forEach(displayMessage);
//         messageLog.scrollTop = messageLog.scrollHeight;
//     }

//     function displayMessage(msg) {
//         if (!msg || !msg.id || seenMessageIds.has(msg.id)) return; // Avoid null/undefined messages and duplicates
//         seenMessageIds.add(msg.id);

//         const messageGroup = document.createElement('div');
//         messageGroup.className = 'message-group';
//         messageGroup.dataset.messageId = msg.id;

//         const avatarDiv = document.createElement('div');
//         avatarDiv.className = 'avatar';
//         updateUserAvatarDisplay(avatarDiv, msg.sender?.username, msg.sender?.avatarUrl);
//         messageGroup.appendChild(avatarDiv);

//         const messageBodyDiv = document.createElement('div');
//         messageBodyDiv.className = 'message-body';

//         const headerDiv = document.createElement('div');
//         headerDiv.className = 'message-header';
//         headerDiv.innerHTML = `
//             <span class="name">${msg.sender?.username || 'System'}</span>
//             <span class="timestamp">${new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
//             ${msg.edited ? '<span class="text-xs text-[var(--text-muted)]">(edited)</span>' : ''}
//         `;
//         messageBodyDiv.appendChild(headerDiv);

//         const contentDiv = document.createElement('div');
//         contentDiv.className = 'message-content';
//         contentDiv.textContent = msg.content || ''; // Handle null/undefined content
//         messageBodyDiv.appendChild(contentDiv);
        
//         // Attachments
//         if (msg.attachments && msg.attachments.length > 0) {
//             msg.attachments.forEach(att => {
//                 if (att.mimeType && att.mimeType.startsWith('image/')) {
//                     const img = document.createElement('img');
//                     img.src = att.fileUrl;
//                     img.alt = att.fileName || 'attachment';
//                     img.className = 'attachment-preview';
//                     img.addEventListener('click', () => window.open(att.fileUrl, '_blank'));
//                     contentDiv.appendChild(img);
//                 } else {
//                     const fileLink = document.createElement('a');
//                     fileLink.href = att.fileUrl;
//                     fileLink.target = '_blank';
//                     fileLink.className = 'file-attachment';
//                     fileLink.textContent = `📎 ${att.fileName || 'Attached File'}`;
//                     contentDiv.appendChild(fileLink);
//                 }
//             });
//         }


//         // Hover Actions (Reply Button)
//         const hoverActionsDiv = document.createElement('div');
//         hoverActionsDiv.className = 'hover-actions';
//         const replyButton = document.createElement('button');
//         replyButton.className = 'action-btn';
//         replyButton.title = 'Reply';
//         replyButton.innerHTML = '↩️'; // Reply emoji
//         replyButton.onclick = () => setReplyTo(msg);
//         hoverActionsDiv.appendChild(replyButton);
//         // Add more actions here (e.g., edit, delete if user has permission)

//         messageGroup.appendChild(messageBodyDiv);
//         messageGroup.appendChild(hoverActionsDiv); // Add actions to the group
//         messageLog.appendChild(messageGroup);
//         messageLog.scrollTop = messageLog.scrollHeight;
//     }

//     function setReplyTo(message) {
//         replyingToMessage = message;
//         updateReplyBar();
//         messageInput.focus();
//     }

//     function updateReplyBar() {
//         if (replyingToMessage) {
//             replyingToUser.textContent = replyingToMessage.sender?.username || 'Unknown';
//             replyingToText.textContent = replyingToMessage.content.substring(0, 50) + (replyingToMessage.content.length > 50 ? '...' : '');
//             replyingToBar.classList.remove('hidden');
//         } else {
//             replyingToBar.classList.add('hidden');
//         }
//     }
//     cancelReplyBtn.addEventListener('click', () => {
//         replyingToMessage = null;
//         updateReplyBar();
//     });


//     function connectWebSocket() {
//         if (stompClient && stompClient.connected) {
//             // If already connected, potentially just change subscription if topic differs
//             const newTopic = currentChatType === 'channel' ?
//                 `/topic/channels/${currentChannelId}/messages` :
//                 `/topic/dm/${currentDmChatId}/messages`;
//             if (activeSubscription && currentChatTopic !== newTopic) {
//                 activeSubscription.unsubscribe();
//                 currentChatTopic = newTopic;
//                 activeSubscription = stompClient.subscribe(currentChatTopic, onMessageReceived);
//                 showToast(`Switched subscription to ${currentChatTopic}`, 'info');
//             } else if (!activeSubscription) { // If connected but no active sub (e.g., after disconnect)
//                  currentChatTopic = newTopic;
//                  activeSubscription = stompClient.subscribe(currentChatTopic, onMessageReceived);
//                  showToast(`Resubscribed to ${currentChatTopic}`, 'info');
//             }
//             sendMessageButton.disabled = false;
//             return;
//         }
//         if (!jwtToken) {
//             showToast('Cannot connect: Not logged in.', 'error');
//             return;
//         }

//         const socket = new SockJS(`${WS_ENDPOINT}?token=${jwtToken}`);
//         stompClient = Stomp.over(socket);
//         stompClient.debug = (str) => { console.log("STOMP: " + str); }; // Enable STOMP debugging

//         const chatId = currentChatType === 'channel' ? currentChannelId : currentDmChatId;
//         chatHeader.classList.remove('status-disconnected', 'status-connecting', 'status-connected');
//         chatHeader.classList.add('status-connecting');
//         chatHeader.textContent = 'Connecting...';

//         stompClient.connect(
//             {}, // STOMP connect headers
//             (frame) => { // On success
//                 chatHeader.classList.remove('status-connecting');
//                 chatHeader.classList.add('status-connected');
//                 chatHeader.textContent = currentChatType === 'channel' ? `# ${chatName.textContent.substring(2)}` : `@ ${chatName.textContent.substring(2)}`;
//                 showToast(`Connected to ${currentChatType} ${chatId}`, 'success');
//                 sendMessageButton.disabled = false;

//                 currentChatTopic = currentChatType === 'channel' ?
//                     `/topic/channels/${chatId}/messages` :
//                     `/topic/dm/${chatId}/messages`;

//                 activeSubscription = stompClient.subscribe(currentChatTopic, onMessageReceived);
//                 // Subscribe to user-specific error queue
//                 stompClient.subscribe('/user/queue/errors', (errorFrame) => {
//                     try {
//                         const errorPayload = JSON.parse(errorFrame.body);
//                         showToast(`Server Error: ${errorPayload.details || errorPayload.error}`, 'error');
//                     } catch (e) {
//                         showToast(`Server Error: ${errorFrame.body}`, 'error');
//                     }
//                 });
//             },
//             (error) => { // On error
//                 console.error('STOMP Connection Error:', error);
//                 chatHeader.classList.remove('status-connecting');
//                 chatHeader.classList.add('status-disconnected');
//                 chatHeader.textContent = `Error: ${error.headers ? error.headers.message : error}`;
//                 showToast(`WebSocket Connection Error: ${error}`, 'error');
//                 sendMessageButton.disabled = true;
//             }
//         );
//     }

//     function disconnectWebSocket() {
//         if (activeSubscription) {
//             activeSubscription.unsubscribe();
//             activeSubscription = null;
//         }
//         if (stompClient && stompClient.connected) {
//             stompClient.disconnect(() => {
//                 showToast('Disconnected from chat.', 'info');
//                 chatHeader.classList.remove('status-connected', 'status-connecting');
//                 chatHeader.classList.add('status-disconnected');
//                 chatHeader.textContent = 'Disconnected';
//             });
//         }
//         sendMessageButton.disabled = true;
//     }

//     function onMessageReceived(payload) {
//         console.log("Raw message received from WebSocket:", payload);
//         try {
//             const message = JSON.parse(payload.body);
//             console.log("Parsed message:", message);
//             displayMessage(message);
//         } catch (e) {
//             console.error("Error parsing incoming message:", e, payload.body);
//             showToast("Received an invalid message from server.", "error");
//         }
//     }

//     sendMessageButton.addEventListener('click', handleSendMessage);
//     messageInput.addEventListener('keypress', (e) => {
//         if (e.key === 'Enter' && !e.shiftKey) {
//             e.preventDefault();
//             handleSendMessage();
//         }
//     });

//     async function handleSendMessage() {
//         const content = messageInput.value.trim();
//         if ((!content && uploadedFiles.length === 0) || !stompClient || !stompClient.connected) {
//             if (!stompClient || !stompClient.connected) showToast('Not connected to chat.', 'error');
//             return;
//         }

//         const messagePayload = {
//             content: content,
//             parentMessageId: replyingToMessage ? replyingToMessage.id : null
//         };

//         if (currentChatType === 'channel') {
//             messagePayload.channelId = currentChannelId;
//         } else if (currentChatType === 'dm') {
//             messagePayload.directMessageChatId = currentDmChatId;
//         } else {
//             showToast('No active chat selected.', 'error');
//             return;
//         }

//         // If there are files, use the REST endpoint for multipart
//         if (uploadedFiles.length > 0) {
//             const formData = new FormData();
//             formData.append('sendMessageDTO', new Blob([JSON.stringify(messagePayload)], { type: 'application/json' }));
//             uploadedFiles.forEach(file => formData.append('files', file));

//             try {
//                 const response = await fetch(`${API_BASE_URL}/messages`, {
//                     method: 'POST',
//                     headers: { 'Authorization': `Bearer ${jwtToken}` /* No Content-Type for FormData */ },
//                     body: formData,
//                 });
//                 if (!response.ok) {
//                     const errorData = await response.json();
//                     throw new Error(errorData.message || `Failed to send message with files: ${response.status}`);
//                 }
//                 // Backend will broadcast this message via WebSocket, so no need to manually add to log here
//                 // unless your backend doesn't echo to sender, then you might parse response.json() and displayMessage()
//                 showToast('Message with files sent!', 'success');
//             } catch (error) {
//                 console.error('Error sending message with files:', error);
//                 showToast(`Error: ${error.message}`, 'error');
//             }
//         } else { // No files, send via WebSocket
//             stompClient.send('/app/chat.sendMessage', {}, JSON.stringify(messagePayload));
//         }

//         messageInput.value = '';
//         uploadedFiles = [];
//         fileUploadPreviewContainer.innerHTML = '';
//         replyingToMessage = null;
//         updateReplyBar();
//         messageInput.style.height = 'auto'; // Reset height
//     }
    
//     // Auto-resize textarea
//     messageInput.addEventListener('input', () => {
//         messageInput.style.height = 'auto';
//         messageInput.style.height = (messageInput.scrollHeight) + 'px';
//     });

//     // --- FILE ATTACHMENT HANDLING ---
//     attachFileButton.addEventListener('click', () => fileUploadInput.click());
//     fileUploadInput.addEventListener('change', (event) => {
//         for (const file of event.target.files) {
//             if (uploadedFiles.length < 5) { // Limit number of files
//                 uploadedFiles.push(file);
//                 renderFileUploadPreview(file);
//             } else {
//                 showToast('Maximum 5 files allowed.', 'warn');
//                 break;
//             }
//         }
//         fileUploadInput.value = ''; // Reset file input
//     });

//     function renderFileUploadPreview(file) {
//         const previewDiv = document.createElement('div');
//         previewDiv.className = 'upload-thumbnail';
        
//         if (file.type.startsWith('image/')) {
//             const img = document.createElement('img');
//             img.src = URL.createObjectURL(file);
//             img.onload = () => URL.revokeObjectURL(img.src);
//             previewDiv.appendChild(img);
//         } else {
//             previewDiv.textContent = file.name.split('.').pop().toUpperCase().substring(0, 4);
//         }

//         const removeBtn = document.createElement('button');
//         removeBtn.className = 'remove-upload';
//         removeBtn.innerHTML = '&times;';
//         removeBtn.onclick = () => {
//             uploadedFiles = uploadedFiles.filter(f => f !== file);
//             previewDiv.remove();
//         };
//         previewDiv.appendChild(removeBtn);
//         fileUploadPreviewContainer.appendChild(previewDiv);
//     }

//     // --- PROFILE MODAL ---
//     userProfileBtn.addEventListener('click', () => {
//         profileDisplayNameInput.value = loggedInUser.username; // Or a separate display name if you implement it
//         profileBioInput.value = loggedInUser.bio || '';
//         updateUserAvatarDisplay(profileModalAvatar, loggedInUser.username, loggedInUser.avatarUrl);
//         profileModalStatus.textContent = '';
//         profileModalContainer.classList.add('show');
//     });

//     changeAvatarButton.addEventListener('click', () => avatarFileInput.click());
//     avatarFileInput.addEventListener('change', (event) => {
//         const file = event.target.files[0];
//         if (file && file.type.startsWith('image/')) {
//             const reader = new FileReader();
//             reader.onload = (e) => {
//                 // Display preview
//                 profileModalAvatar.innerHTML = `<img src="${e.target.result}" alt="avatar preview" class="w-full h-full object-cover rounded-full"/>`;
//                 // Store for potential upload, not implemented here
//                 // loggedInUser.newAvatarFile = file; 
//             };
//             reader.readAsDataURL(file);
//         } else if (file) {
//             showToast('Please select an image file.', 'warn');
//         }
//     });

//     cancelProfileButton.addEventListener('click', () => {
//         profileModalContainer.classList.remove('show');
//     });

//     saveProfileButton.addEventListener('click', async () => {
//         const newDisplayName = profileDisplayNameInput.value.trim();
//         const newBio = profileBioInput.value.trim();

//         // Basic validation
//         if (!newDisplayName) {
//             profileModalStatus.textContent = 'Display name cannot be empty.';
//             profileModalStatus.className = 'text-sm text-center text-red-500 h-4';
//             return;
//         }
//         profileModalStatus.textContent = 'Saving...';
//         profileModalStatus.className = 'text-sm text-center text-yellow-500 h-4';

//         // --- !!! ---
//         // TODO: Implement backend API call to save profile data
//         // Example:
//         // try {
//         //   const formData = new FormData();
//         //   formData.append('displayName', newDisplayName);
//         //   formData.append('bio', newBio);
//         //   if (avatarFileInput.files[0]) { // If a new avatar was selected
//         //     formData.append('avatar', avatarFileInput.files[0]);
//         //   }
//         //   const response = await fetch(`${API_BASE_URL}/users/me/profile`, { // Assuming this endpoint
//         //     method: 'PUT', // or POST
//         //     headers: { 'Authorization': `Bearer ${jwtToken}` },
//         //     body: formData
//         //   });
//         //   if (!response.ok) {
//         //     const errData = await response.json();
//         //     throw new Error(errData.message || 'Failed to save profile');
//         //   }
//         //   const updatedUser = await response.json();
//         //   loggedInUser.username = updatedUser.username; // Or updatedUser.displayName
//         //   loggedInUser.avatarUrl = updatedUser.avatarUrl;
//         //   loggedInUser.bio = updatedUser.bio;
//         //   updateUserAvatarDisplay(userProfileBtn, loggedInUser.username, loggedInUser.avatarUrl);
//         //   showToast('Profile saved successfully!', 'success');
//         //   profileModalContainer.classList.remove('show');
//         // } catch (error) {
//         //   console.error("Save profile error:", error);
//         //   profileModalStatus.textContent = `Error: ${error.message}`;
//         //   profileModalStatus.className = 'text-sm text-center text-red-500 h-4';
//         // }
//         // --- !!! ---
        
//         // For now, just update locally and close (simulation)
//         loggedInUser.username = newDisplayName; // Assuming username is the display name for now
//         loggedInUser.bio = newBio;
//         // If avatarFileInput.files[0] exists, you'd typically upload it and get a new URL from backend
//         // For simulation, if a new avatar was previewed, we could set loggedInUser.avatarUrl to its data URL
//         // but this is not ideal for persistence.
//         if (avatarFileInput.files[0] && profileModalAvatar.querySelector('img')) {
//              loggedInUser.avatarUrl = profileModalAvatar.querySelector('img').src; // This is a data URL, not a persistent one
//         }

//         updateUserAvatarDisplay(userProfileBtn, loggedInUser.username, loggedInUser.avatarUrl);
//         showToast('Profile updated (simulated).', 'info');
//         profileModalContainer.classList.remove('show');
//         avatarFileInput.value = ''; // Clear file input
//     });


//     // --- INITIALIZATION ---
//     // Check for existing token (e.g., from localStorage) could go here for auto-login
//     // For now, always start with login screen.
//     sendMessageButton.disabled = true;
//     disconnectButton.disabled = true;

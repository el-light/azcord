    // Constants for API and WebSocket endpoints
    const API_BASE_URL = 'http://localhost:8082/api';
    const WS_ENDPOINT = 'http://localhost:8082/ws';
    const WS_REACTIONS_DEST = '/topic/channels'; // used below
    
    // ---------------------------------------------------------------------------
    // Unified fetch wrapper: always sends JWT and stops silent redirects to /login
    function api(url, opts = {}) {
      const headers = { ...opts.headers };
      if (jwtToken) headers['Authorization'] = `Bearer ${jwtToken}`;

      return fetch(url, { ...opts, headers, redirect: 'manual' })
        .then(res => {
          if (res.status === 401 || res.type === 'opaqueredirect') {
            showToast('Session expired. Please log in again.', 'error');
            location.reload();          // back to login screen
            throw new Error('unauthorized');
          }
          return res;
        });
    }
    // ---------------------------------------------------------------------------
    
    // Helper for making file URLs absolute
    const API_ORIGIN = location.origin.includes(':5500') 
                     ? 'http://localhost:8082'       // dev: front on 5500, API on 8080
                     : location.origin;              // prod: same host

    function absUrl(path){
      if(!path) return '/img/default-avatar.png';
      if(path.startsWith('http')) return path;
      return API_ORIGIN + (path.startsWith('/') ? path : '/api/files/'+path);
    }

    /*  ---------- one tiny light‑box ---------- */
    function showLightBox(src){
      const box = document.createElement('div');
      box.className = 'avatar-box';
      box.innerHTML = `<img src="${src}">`;
      box.onclick = () => box.remove();
      document.body.appendChild(box);
    }
    
    /*  ---------- profile card popup ---------- */
    function showProfileCard(user, x, y){
      const card = document.createElement('div');
      card.className='profile-pop';
      card.style.left = x+'px'; card.style.top = y+'px';

      card.innerHTML =
        `<img id="popAv" src="${absUrl(user.avatarUrl)}">
         <h4>${user.username}</h4>
         <p>${user.bio||''}</p>`;

      card.onclick = e=>e.stopPropagation();          // keep open if click inside
      document.body.appendChild(card);

      // click avatar -> full‑screen
      card.querySelector('#popAv').onclick = e=>{
         e.stopPropagation(); showLightBox(absUrl(user.avatarUrl));
      };

      // click outside closes
      const closer = ()=>{card.remove();document.removeEventListener('click',closer)};
      setTimeout(()=>document.addEventListener('click',closer),0);
    }

    // Application state variables
    let jwtToken = null;
    let jwtTokenExpiry = null; // Add this variable to track token expiration
    let loggedInUser = { username: null, id: null, avatarUrl: null, bio: null };
    let currentServerId = null;
    let currentChannelId = null;
    let currentDmChatId = null;
    let currentChatType = null; // 'channel' or 'dm'
    let stompClient = null;
    let activeSubscriptions = {}; 
    const messageHistory = new Map(); 
    const seenMessageIds = new Set(); 
    let uploadedFiles = []; 
    let replyingToMessage = null; 
    const availableReactions = ['👍', '❤️', '😂', '🎉', '😢', '😮', '🤔'];
    let typingUsers = new Map();          // uid -> timestamp and name

    /* --- DM helpers --- */
    async function openOrCreateDm(friendId){
       const r = await api(`${API_BASE_URL}/dm-chats/with/${friendId}`, {
           method:'POST'
       });
       if(!r.ok){ showToast('DM error '+r.status, 'error'); return null; }
       return r.json();
    }

    function openDmChat(dto){
       currentChannelId = null;
       currentDmChatId = dto.id;
       currentChatType = 'dm';
       
       const otherUser = dto.participants.find(u => u.id !== loggedInUser.id);
       chatName.textContent = '@ ' + otherUser.username;
       messageLog.innerHTML = '';
       seenMessageIds.clear();
       
       // Optional: hide channel list
       // channelListBar.classList.add('hidden');
       
       loadDmHistory();
       connectWebSocket();
    }

    async function loadDmHistory(){
       const r = await api(`${API_BASE_URL}/dm-chats/${currentDmChatId}/messages?page=0&size=50`, {});
       if(!r.ok) {
           showToast('Failed to load messages: ' + r.status, 'error');
           return;
       }
       const page = await r.json();
       const messages = page.content || [];
       messages.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
       
       messageHistory.set(`dm-${currentDmChatId}`, messages);
       renderMessages(messages);
    }

    // DOM Element references
    const $ = (selector) => document.querySelector(selector);
    const $$ = (selector) => document.querySelectorAll(selector);

    const loginScreen = $('#loginScreen');
    const appScreen = $('#appScreen');
    const loginUsernameInput = $('#loginUsername');
    const loginPasswordInput = $('#loginPassword');
    const loginButton = $('#loginButton');
    const loginStatus = $('#loginStatus');

    const serverBar = $('#serverBar');
    const dynamicServerIconsContainer = $('#dynamicServerIconsContainer'); 
    const userProfileBtn = $('#userProfileBtn');
    const channelListBar = $('#channelListBar');
    const serverNameHeader = $('#serverNameHeader h2');
    const channelsAndDmsList = $('#channelsAndDmsList');

    const chatView = $('#chatView');
    const chatHeader = $('#chatHeader'); 
    const chatName = $('#chatName'); 
    const disconnectChatButton = $('#disconnectChatButton'); 
    const messageLog = $('#messageLog');
    const messageInput = $('#messageInput');
    const sendMessageButton = $('#sendMessageButton');
    const attachFileButton = $('#attachFileButton');
    const fileUploadInput = $('#fileUploadInput');
    const fileUploadPreviewContainer = $('#fileUploadPreviewContainer');
    const replyingToBar = $('#replyingToBar');
    const replyingToUser = $('#replyingToUser');
    const replyingToText = $('#replyingToText');
    const cancelReplyBtn = $('#cancelReplyBtn');
    const typingIndicator = $('#typingIndicator');

    const profileModalContainer = $('#profileModalContainer');
    const profileModalAvatar = $('#profileModalAvatar');
    const changeAvatarButton = $('#changeAvatarButton');
    const avatarFileInput = $('#avatarFileInput');
    const profileDisplayNameInput = $('#profileDisplayName');
    const profileBioInput = $('#profileBio');
    const saveProfileButton = $('#saveProfileButton');
    const cancelProfileButton = $('#cancelProfileButton');
    const profileModalStatus = $('#profileModalStatus');


    // --- UTILITY FUNCTIONS ---
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = 'toast-notification';
        toast.textContent = message;
        if (type === 'error') toast.style.backgroundColor = 'var(--danger)';
        if (type === 'success') toast.style.backgroundColor = 'var(--success)';
        document.body.appendChild(toast);
        toast.addEventListener('animationend', () => toast.remove());
    }

    function createAvatarPlaceholder(name) {
        return name ? name.charAt(0).toUpperCase() : '?';
    }

    // Debug function to check avatar URL
    async function debugCheckAvatarUrl(path){
       const url = absUrl(path);           // always correct host
       try{
          const r = await fetch(url,{method:'HEAD'});
          if(!r.ok) throw new Error(r.status);
          console.log("✅ Avatar URL is accessible:", url);
       }catch(e){
          console.warn('Avatar HEAD check failed: ', e);
       }
    }

    function updateUserAvatarDisplay(element, username, avatarUrl) {
        // For debugging, log when we're updating an avatar
        console.log(`Updating avatar for ${username || 'unknown'} with URL: ${avatarUrl}`);
        
        if (avatarUrl) {
            // Use our helper function to ensure absolute URLs
            const finalUrl = absUrl(avatarUrl);
            console.log(`Converted to absolute URL: ${finalUrl}`);
            
            // Create an image element to test loading
            const img = new Image();
            img.onload = () => {
                // Image loaded successfully, update the element
                element.innerHTML = `<img src="${finalUrl}" alt="${username || 'avatar'}" class="w-full h-full object-cover rounded-full">`;
                // Add click handler for lightbox
                element.onclick = () => showLightBox(finalUrl);
                console.log(`✅ Successfully loaded avatar for ${username || 'unknown'}`);
            };
            img.onerror = (e) => {
                // Image failed to load, try with a different URL format
                console.error(`❌ Failed to load avatar for ${username || 'unknown'} with URL: ${finalUrl}`, e);
                
                // Fallback to avatar placeholder
                createAvatarPlaceholder(element, username);
            };
            img.src = finalUrl;
        } else {
            createAvatarPlaceholder(element, username);
        }
    }
    
    function createAvatarPlaceholder(element, username) {
        let hash = 0;
        for (let i = 0; i < (username || "").length; i++) {
            hash = username.charCodeAt(i) + ((hash << 5) - hash);
        }
        const color = `hsl(${hash % 360}, 50%, 60%)`; // Adjusted saturation and lightness for better visibility
        element.style.backgroundColor = color;
        element.innerHTML = `<span class="flex items-center justify-center w-full h-full text-xl">${username ? username.charAt(0).toUpperCase() : '?'}</span>`;
    }

    // --- AUTHENTICATION ---
    loginButton.addEventListener('click', async () => {
        const username = loginUsernameInput.value.trim();
        const password = loginPasswordInput.value.trim();
        if (!username || !password) {
            loginStatus.textContent = 'Username and password are required.';
            return;
        }
        loginStatus.textContent = 'Logging in...';
        try {
            const response = await api(`${API_BASE_URL}/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password }),
            });
            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || `HTTP error! Status: ${response.status}`);
            }
            jwtToken = await response.text();
            loggedInUser.username = username; // Set initial username
            
            // Extract token expiry time
            try {
                const payload = JSON.parse(atob(jwtToken.split('.')[1]));
                jwtTokenExpiry = payload.exp * 1000; // Convert to milliseconds
                console.log("Token received, expiry:", new Date(jwtTokenExpiry).toLocaleString());
                // Check if token is already expired (should not happen but just in case)
                const currentTime = new Date().getTime();
                if (jwtTokenExpiry <= currentTime) {
                    throw new Error("Received an expired token from server");
                }
            } catch (e) {
                console.error("Error processing JWT token:", e);
                throw new Error("Invalid token received from server");
            }
            
            // Fetch full user details after successful login
            try {
               const userDetailsResponse = await api(`${API_BASE_URL}/users/me`, {});
               if (userDetailsResponse.ok) {
                   const userDetails = await userDetailsResponse.json();
                   loggedInUser.id = userDetails.id; 
                   loggedInUser.username = userDetails.username; // Update with potentially canonical username
                   loggedInUser.avatarUrl = userDetails.avatarUrl;
                   loggedInUser.bio = userDetails.bio;
               } else { 
                console.warn("Could not fetch full user details after login. Using provided username.");
                // Attempt to get ID from JWT if /users/me fails (less ideal)
                try {
                    const payload = JSON.parse(atob(jwtToken.split('.')[1]));
                    // Try common JWT claims for user ID
                    loggedInUser.id = payload.userId || payload.id || parseInt(payload.sub); 
                    if (isNaN(loggedInUser.id)) loggedInUser.id = null; // Ensure it's a number or null
                    console.log("Attempted to get user ID from JWT payload:", loggedInUser.id);
                } catch (e) { console.error("Could not parse JWT for ID", e);}
               }
            } catch (e) { 
                console.warn("Error fetching user details:", e);
                showToast("Could not fetch user details. Some features might be limited.", "warn");
            }


            loginStatus.textContent = '';
            loginScreen.classList.add('hidden');
            appScreen.classList.remove('hidden');
            updateUserAvatarDisplay(userProfileBtn, loggedInUser.username, loggedInUser.avatarUrl);
            await loadServersAndDMs();
            showToast(`Welcome back, ${loggedInUser.username}!`, 'success');
        } catch (error) {
            loginStatus.textContent = `Login failed: ${error.message}`;
            console.error('Login error:', error);
        }
    });

    // --- SERVER AND DM LOADING & RENDERING ---
    async function loadServersAndDMs() {
        if (!jwtToken) return;
        dynamicServerIconsContainer.innerHTML = '<p class="text-xs text-[var(--text-muted)] p-2">Loading servers...</p>';
        
        // Clear entire channel/DM list before re-rendering both sections
        channelsAndDmsList.innerHTML = '<p class="px-2 py-1 text-sm text-[var(--text-muted)]">Loading channels & DMs...</p>'; 

        let dmsData = []; 
        let serversData = [];

        try {
            // Fetch Servers
            const serversResponse = await api(`${API_BASE_URL}/servers`, {});
            if (!serversResponse.ok) throw new Error(`Failed to fetch servers: ${serversResponse.status} ${await serversResponse.text()}`);
            serversData = await serversResponse.json();
            
            // Fetch DMs
            const dmsResponse = await api(`${API_BASE_URL}/dm-chats`, {});
            if (!dmsResponse.ok) throw new Error(`Failed to fetch DMs: ${dmsResponse.status} ${await dmsResponse.text()}`);
            dmsData = await dmsResponse.json();
            
            // Render servers first, then clear channel/DM list and render DMs
            // This order ensures that if selectServer is called, it has the DMs to re-append if needed.
            renderServerList(serversData); 
            
            channelsAndDmsList.innerHTML = ''; // Clear for fresh render of DMs (and channels if a server is selected later)
            renderDmList(dmsData); // Render DMs into the (now potentially empty) list

        } catch (error) {
            console.error('Error loading servers/DMs:', error);
            showToast(`Error loading initial data: ${error.message}`, 'error');
            if (dynamicServerIconsContainer.innerHTML.includes('Loading')) {
                 dynamicServerIconsContainer.innerHTML = '<p class="text-xs text-[var(--danger)] p-2">Error loading servers.</p>';
            }
            // Update the channelsAndDmsList with a general error if it's still in loading state
            if (channelsAndDmsList.innerHTML.includes('Loading')) {
                 channelsAndDmsList.innerHTML = '<p class="px-2 py-1 text-sm text-[var(--danger)]">Error loading lists.</p>';
            }
        }
    }

    function renderServerList(servers) {
        dynamicServerIconsContainer.innerHTML = ''; 
        if (!servers || servers.length === 0) {
            const noServersMsg = document.createElement('p');
            noServersMsg.className = 'text-xs text-center text-[var(--text-muted)] p-2';
            noServersMsg.textContent = 'No servers. Create one!';
            dynamicServerIconsContainer.appendChild(noServersMsg);
        } else {
            servers.forEach(server => {
                const serverIcon = document.createElement('div');
                serverIcon.className = 'server-icon';
                serverIcon.title = server.name;
                // Use server.avatarUrl (from your backend mapping) or server.iconUrl
                updateUserAvatarDisplay(serverIcon, server.name, server.avatarUrl || server.iconUrl);
                serverIcon.addEventListener('click', () => selectServer(server));
                serverIcon.addEventListener('contextmenu', (e) => {
                    e.preventDefault();
                    handleServerIconUploadPrompt(server); 
                });
                dynamicServerIconsContainer.appendChild(serverIcon);
            });
        }
        
        const addServerIcon = document.createElement('div');
        addServerIcon.className = 'server-icon';
        addServerIcon.textContent = '+';
        addServerIcon.title = 'Create New Server';
        addServerIcon.addEventListener('click', handleCreateServerPrompt);
        dynamicServerIconsContainer.appendChild(addServerIcon);
    }

    function renderDmList(dms) {
        // Remove only old DM items and header, preserving channels if a server is selected
        $$('#channelsAndDmsList .dm-list-header').forEach(el => el.remove());
        $$('#channelsAndDmsList .channel-item[data-dm-id]').forEach(el => el.remove());

        const dmHeader = document.createElement('h3');
        dmHeader.className = 'dm-list-header list-header'; // Use shared list-header style
        dmHeader.textContent = 'Direct Messages';
        channelsAndDmsList.appendChild(dmHeader); 

        if (!dms || dms.length === 0) {
            const noDmsMsg = document.createElement('p');
            noDmsMsg.className = 'channel-item italic';
            noDmsMsg.textContent = 'No DMs yet.';
            channelsAndDmsList.appendChild(noDmsMsg);
            return;
        }

        dms.forEach(dm => {
            let dmDisplayName = dm.name; 
            let dmAvatarUrl = null; 
            let otherParticipant = null;
            
            if (dm.chatType === 'DIRECT_MESSAGE' && dm.participants) { 
                otherParticipant = dm.participants.find(p => p.username !== loggedInUser.username);
                dmDisplayName = otherParticipant ? otherParticipant.username : (dm.participants.length === 1 ? 'Notes to Self' : `DM #${dm.id}`);
                dmAvatarUrl = otherParticipant ? otherParticipant.avatarUrl : null; 
            } else if (!dmDisplayName && dm.participants) { 
                 const otherParticipants = dm.participants.filter(p => p.username !== loggedInUser.username);
                 dmDisplayName = otherParticipants.map(p=>p.username).slice(0,2).join(', ');
                 if (otherParticipants.length > 2) dmDisplayName += ` & ${otherParticipants.length - 2} more`;
                 else if (otherParticipants.length === 0 && dm.participants.length === 1) dmDisplayName = "Notes to Self";
            }
            if (!dmDisplayName) dmDisplayName = `Group DM #${dm.id}`;


            const dmItem = document.createElement('div');
            dmItem.className = 'channel-item';
            dmItem.dataset.dmId = dm.id;
            dmItem.id = `dm-${dm.id}`;
            
            // Add username data attribute if this is a direct message with another user
            if (otherParticipant) {
                dmItem.dataset.username = otherParticipant.username;
                // Add user ID if available
                if (otherParticipant.id) {
                    dmItem.dataset.userId = otherParticipant.id;
                }
            }

            const avatarPlaceholder = document.createElement('span');
            avatarPlaceholder.className = 'avatar'; 
            // Add username data attribute to avatar as well
            if (otherParticipant) {
                avatarPlaceholder.dataset.username = otherParticipant.username;
                // Add user ID if available
                if (otherParticipant.id) {
                    avatarPlaceholder.dataset.userId = otherParticipant.id;
                }
            }
            
            // Use absUrl directly instead of updateUserAvatarDisplay
            if (dmAvatarUrl) {
                const avatarSrc = absUrl(dmAvatarUrl);
                avatarPlaceholder.innerHTML = `<img src="${avatarSrc}" alt="${dmDisplayName || 'avatar'}" class="w-full h-full object-cover rounded-full">`;
                // Add profile card popup on avatar click
                avatarPlaceholder.onclick = e => {
                    e.stopPropagation();
                    if (otherParticipant) {
                        showProfileCard(otherParticipant, e.clientX+10, e.clientY-20);
                    }
                };
            } else {
                createAvatarPlaceholder(avatarPlaceholder, dmDisplayName);
            }
            
            const nameSpan = document.createElement('span');
            nameSpan.className = 'truncate';
            nameSpan.textContent = dmDisplayName;

            dmItem.appendChild(avatarPlaceholder);
            dmItem.appendChild(nameSpan);
            dmItem.addEventListener('click', () => selectDmChat(dm));
            channelsAndDmsList.appendChild(dmItem);
        });
    }
    
    async function handleCreateServerPrompt() {
        const serverName = prompt("Enter a name for your new server:");
        if (serverName && serverName.trim() !== "") {
            try {
                const response = await api(`${API_BASE_URL}/servers`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: serverName.trim() })
                });
                if (!response.ok) {
                    const errorText = await response.text();
                    let errorMessage = `Server creation failed: ${response.status}`;
                    try {
                        const errorJson = JSON.parse(errorText);
                        errorMessage = errorJson.message || errorJson.error || errorMessage;
                    } catch (e) { /* ignore if not json */ }
                    throw new Error(errorMessage);
                }
                showToast(`Server "${serverName}" created!`, 'success');
                await loadServersAndDMs(); 
            } catch (error) {
                showToast(error.message, 'error');
                console.error("Create server error:", error);
            }
        }
    }
    
    async function handleServerIconUploadPrompt(server) {
        if (!server || (!server.server_id && !server.id)) {
            showToast("Invalid server data for icon upload.", "error");
            return;
        }
        const serverIdToUse = server.server_id || server.id;

        const fileInput = document.createElement('input');
        fileInput.type = 'file';
        fileInput.accept = 'image/*';
        fileInput.onchange = async (e) => {
            const file = e.target.files[0];
            if (!file) return;
            const formData = new FormData();
            formData.append('icon', file);
            try {
                const response = await api(`${API_BASE_URL}/servers/${serverIdToUse}/icon`, {
                    method: 'PUT',
                    body: formData
                });
                if (!response.ok) {
                     const errorText = await response.text();
                     throw new Error(errorText || `Failed to upload server icon: ${response.status}`);
                }
                showToast(`Icon for server "${server.name}" updated!`, 'success');
                await loadServersAndDMs(); 
            } catch (error) {
                showToast(`Icon upload error: ${error.message}`, 'error');
                console.error("Server icon upload error:", error);
            }
        };
        fileInput.click();
    }


    function selectServer(server) {
        $$('#dynamicServerIconsContainer .server-icon').forEach(icon => icon.classList.remove('active'));
        const serverIcons = Array.from($$('#dynamicServerIconsContainer .server-icon'));
        const clickedIcon = serverIcons.find(icon => icon.title === server.name); 
        if(clickedIcon) clickedIcon.classList.add('active');

        currentServerId = server.server_id || server.id; 
        window.currentServerId = currentServerId; // expose for roles.js
        window.currentServerData = server;      // keep full details in memory
        serverNameHeader.textContent = server.name;
        
        // Clear channels, but keep DMs
        const dmElementsToKeep = [];
        let dmHeaderElement = channelsAndDmsList.querySelector('.dm-list-header');
        if(dmHeaderElement) dmElementsToKeep.push(dmHeaderElement);
        $$('#channelsAndDmsList .channel-item[data-dm-id]').forEach(el => dmElementsToKeep.push(el));
        
        channelsAndDmsList.innerHTML = ''; // Clear all
        
        if (server.channels && server.channels.length > 0) {
            renderChannelList(server.channels); 
        } else {
            const noChannels = document.createElement('p');
            noChannels.className = 'px-2 py-1 text-sm text-[var(--text-muted)] italic';
            noChannels.textContent = 'No text channels here.';
            channelsAndDmsList.appendChild(noChannels);
        }
        dmElementsToKeep.forEach(el => channelsAndDmsList.appendChild(el)); // Re-add DMs


        chatName.textContent = `# select-a-channel`;
        messageLog.innerHTML = '<p class="text-center text-[var(--text-muted)]">Select a channel or DM.</p>';
        disconnectWebSocket();
        currentChannelId = null;
        currentDmChatId = null;
        currentChatType = null;
        sendMessageButton.disabled = true;
        disconnectChatButton.classList.add('hidden');
    }

    function renderChannelList(channels) {
         // const channelHeader = document.createElement('h3');
        // channelHeader.className = 'list-header'; 
        // channelHeader.textContent = 'Text Channels';
        // channelsAndDmsList.appendChild(channelHeader); // Appends after DMs if DMs were rendered first

        // New header structure for Text Channels
        const channelListHeaderContainer = document.createElement('div');
        channelListHeaderContainer.className = 'list-header flex items-center justify-between px-2 pt-2'; // Adjusted padding/margin as needed
        channelListHeaderContainer.innerHTML = `
            <span class="uppercase font-semibold text-xs text-[var(--text-muted)]">Text Channels</span>
            <button id="addChannelBtn" title="Create Channel" class="text-lg leading-none text-[var(--interactive-normal)] hover:text-[var(--interactive-hover)]">＋</button>
        `;
        // Prepend this header to the channelsAndDmsList or a specific container for channels if DMs are separate
        // If channelsAndDmsList contains both, ensure this is placed before channel items and after DM header/items if DMs come first.
        // For now, prepending to channelsAndDmsList directly assuming it's cleared or managed correctly before this render.
        const firstChild = channelsAndDmsList.firstChild;
        if (firstChild) {
            channelsAndDmsList.insertBefore(channelListHeaderContainer, firstChild);
        } else {
            channelsAndDmsList.appendChild(channelListHeaderContainer);
        }

        if (!channels || channels.length === 0) {
            const noChannelsMsg = document.createElement('p');
            noChannelsMsg.className = 'channel-item italic';
            noChannelsMsg.textContent = 'No channels here.';
            channelsAndDmsList.appendChild(noChannelsMsg);
            return;
        }

        channels.forEach(channel => {
            const channelItem = document.createElement('div');
            channelItem.className = 'channel-item';
            channelItem.dataset.channelId = channel.id;
            channelItem.innerHTML = `<span class="icon">#</span> <span class="truncate">${channel.name}</span>`;
            channelItem.addEventListener('click', () => selectChannel(channel));
            channelsAndDmsList.appendChild(channelItem);
        });
    }

    function selectChannel(channel) {
        console.log("Selected channel:", channel);
        if (currentChannelId === channel.id && currentChatType === 'channel') return; 
        currentChannelId = channel.id;
        currentDmChatId = null;
        currentChatType = 'channel';
        chatName.textContent = `# ${channel.name}`;
        messageInput.placeholder = `Message #${channel.name}`;
        setActiveChannelItem(channel.id);
        loadAndDisplayMessages();
    }

    function selectDmChat(dm) {
        console.log("Selected DM:", dm);
        if (currentDmChatId === dm.id && currentChatType === 'dm') return; 
        currentDmChatId = dm.id;
        currentChannelId = null;
        currentChatType = 'dm';
        
        let dmDisplayName = dm.name;
        if (dm.chatType === 'DIRECT_MESSAGE' && dm.participants) {
            const otherParticipant = dm.participants.find(p => p.username !== loggedInUser.username);
            dmDisplayName = otherParticipant ? otherParticipant.username : (dm.participants.length === 1 ? 'Notes to Self' : `DM #${dm.id}`);
        } else if (!dmDisplayName && dm.participants) { 
             const otherParticipants = dm.participants.filter(p => p.username !== loggedInUser.username);
             dmDisplayName = otherParticipants.map(p=>p.username).slice(0,2).join(', ');
             if (otherParticipants.length > 2) dmDisplayName += ` & ${otherParticipants.length - 2} more`;
             else if (otherParticipants.length === 0 && dm.participants.length === 1) dmDisplayName = "Notes to Self";
        }
        if (!dmDisplayName) dmDisplayName = `Group DM #${dm.id}`;


        chatName.textContent = `@ ${dmDisplayName}`;
        messageInput.placeholder = `Message @${dmDisplayName}`;
        setActiveChannelItem(dm.id, true); 
        loadAndDisplayMessages();
    }
    
    function setActiveChannelItem(id, isDm = false) {
        $$('#channelsAndDmsList .channel-item').forEach(item => item.classList.remove('active'));
        const items = $$('#channelsAndDmsList .channel-item');
        for(let item of items){
            const itemId = isDm ? item.dataset.dmId : item.dataset.channelId;
            if(itemId == id){ 
                 item.classList.add('active');
                 break;
            }
        }
    }

    // --- MESSAGE HANDLING & WEBSOCKETS ---
    async function loadAndDisplayMessages() {
        if (!currentChatType || (!currentChannelId && !currentDmChatId)) {
            messageLog.innerHTML = '<p class="text-center text-[var(--text-muted)] p-10">Select a channel or DM to start chatting.</p>';
            sendMessageButton.disabled = true;
            disconnectChatButton.classList.add('hidden');
            return;
        }
        messageLog.innerHTML = '<p class="text-center text-[var(--text-muted)] p-10">Loading messages...</p>';
        seenMessageIds.clear(); 
        replyingToMessage = null;
        updateReplyBar();

        const chatId = currentChatType === 'channel' ? currentChannelId : currentDmChatId;
        const cacheKey = `${currentChatType}-${chatId}`;
        
        try {
            const endpoint = currentChatType === 'channel' ?
                `${API_BASE_URL}/channels/${currentChannelId}/messages?page=0&size=50` :
                `${API_BASE_URL}/dm-chats/${currentDmChatId}/messages?page=0&size=50`;

            const response = await api(endpoint, {});
            if (!response.ok) {
                 const errorText = await response.text();
                 let errorMessage = `Failed to fetch messages: ${response.status}`;
                 try { const errorJson = JSON.parse(errorText); errorMessage = errorJson.message || errorJson.error || errorMessage; } catch (e) {}
                 throw new Error(errorMessage);
            }
            const page = await response.json();
            const messages = page.content || [];
            messages.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)); 
            
            messageHistory.set(cacheKey, messages); 
            renderMessages(messages);
            connectWebSocket();
            if(currentChatType === 'channel') subscribeReactions(currentChannelId);

        } catch (error) {
            console.error('Error loading messages:', error);
            messageLog.innerHTML = `<p class="text-center text-[var(--danger)] p-10">Error loading messages: ${error.message}</p>`;
            disconnectWebSocket(); 
        }
    }
    
    function renderMessages(messages) {
        messageLog.innerHTML = ''; 
        if (messages.length === 0) {
            messageLog.innerHTML = '<p class="text-center text-[var(--text-muted)] p-10">No messages yet. Be the first to say something!</p>';
        } else {
            messages.forEach(msg => displayMessage(msg, false)); 
        }
        messageLog.scrollTop = messageLog.scrollHeight;
    }

    function displayMessage(msg, prepend = false) {
        if (!msg || !msg.id ) { 
            console.warn("displayMessage called with invalid or no-ID message:", msg);
            return;
        }
        if(seenMessageIds.has(msg.id) && !prepend && !msg.isUpdate) { // Added !msg.isUpdate check
            console.log("Skipping already seen message ID (not an update):", msg.id);
            return;
        }
        if (!prepend) { // Only add to seen if it's a new message append, not history or update
            seenMessageIds.add(msg.id);
        }


        const messageGroup = document.createElement('div');
        messageGroup.className = 'message-group';
        messageGroup.dataset.messageId = msg.id;
        // Add sender username as data attribute for easier avatar updates
        if (msg.sender && msg.sender.username) {
            messageGroup.dataset.senderUsername = msg.sender.username;
        }

        const avatarDiv = document.createElement('div');
        avatarDiv.className = 'avatar';
        // Add data attribute to the avatar element as well
        if (msg.sender && msg.sender.username) {
            avatarDiv.dataset.username = msg.sender.username;
        }
        if (msg.sender && msg.sender.id) {
            avatarDiv.dataset.sender = msg.sender.id;
        }
        
        // Use getAvatar helper instead of updateUserAvatarDisplay for message avatars
        if (msg.sender && msg.sender.avatarUrl) {
            const avatarSrc = absUrl(msg.sender.avatarUrl);
            avatarDiv.innerHTML = `<img src="${avatarSrc}" alt="${msg.sender.username || 'avatar'}" class="w-full h-full object-cover rounded-full">`;
            avatarDiv.onclick = e => {
                e.stopPropagation();
                showProfileCard(msg.sender, e.clientX+10, e.clientY-20);
            };
        } else {
            createAvatarPlaceholder(avatarDiv, msg.sender?.username);
        }
        
        messageGroup.appendChild(avatarDiv);

        const messageBodyDiv = document.createElement('div');
        messageBodyDiv.className = 'message-body';

        // Display replied-to message snippet
        // Assumes backend MessageDTO now has 'repliedTo: { senderUsername, contentSnippet }'
        if (msg.repliedTo && msg.repliedTo.senderUsername && msg.repliedTo.contentSnippet) {
            const repliedToDiv = document.createElement('div');
            repliedToDiv.className = 'replied-to-message';
            repliedToDiv.innerHTML = `
                <span class="icon mr-1">↩️</span> Replying to <strong>${msg.repliedTo.senderUsername}</strong>:
                <span class="truncate inline-block max-w-xs italic">"${msg.repliedTo.contentSnippet}..."</span>
            `;
            messageBodyDiv.appendChild(repliedToDiv);
        }


        const headerDiv = document.createElement('div');
        headerDiv.className = 'message-header';
        headerDiv.innerHTML = `
            <span class="name">${msg.sender?.username || 'System'}</span>
            <span class="timestamp">${new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
            ${msg.edited ? '<span class="text-xs text-[var(--text-muted)]">(edited)</span>' : ''}
        `;
        messageBodyDiv.appendChild(headerDiv);

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.textContent = msg.content || ''; 
        messageBodyDiv.appendChild(contentDiv);
        
        if (msg.attachments && msg.attachments.length > 0) {
            msg.attachments.forEach(att => {
                if (att.mimeType && att.mimeType.startsWith('image/')) {
                    const img = document.createElement('img');
                    img.src = absUrl(att.fileUrl);
                    img.alt = att.fileName || 'attachment';
                    img.className = 'attachment-preview';
                    img.addEventListener('click', () => window.open(absUrl(att.fileUrl), '_blank'));
                    contentDiv.appendChild(img);
                } else {
                    const fileLink = document.createElement('a');
                    fileLink.href = absUrl(att.fileUrl);
                    fileLink.target = '_blank';
                    fileLink.className = 'file-attachment';
                    fileLink.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg><span>${att.fileName || 'Attached File'}</span>`;
                    contentDiv.appendChild(fileLink);
                }
            });
        }
        
        const reactionsBar = document.createElement('div');
        reactionsBar.className = 'reaction-bar';
        messageBodyDiv.appendChild(reactionsBar);
        updateMessageReactionsDisplay(reactionsBar, msg);


        const hoverActionsDiv = document.createElement('div');
        hoverActionsDiv.className = 'hover-actions';
        const replyButton = document.createElement('button');
        replyButton.className = 'action-btn';
        replyButton.title = 'Reply';
        replyButton.innerHTML = '↩️'; 
        replyButton.onclick = () => setReplyTo(msg);
        hoverActionsDiv.appendChild(replyButton);
        
        messageGroup.appendChild(messageBodyDiv); 
        messageGroup.appendChild(hoverActionsDiv); 
        
        if (prepend) {
            messageLog.insertBefore(messageGroup, messageLog.firstChild);
        } else {
            const shouldScroll = messageLog.scrollTop + messageLog.clientHeight >= messageLog.scrollHeight - 50; 
            messageLog.appendChild(messageGroup);
            if (shouldScroll) {
                 messageLog.scrollTop = messageLog.scrollHeight;
            }
        }
        // Apply role color after message is in DOM and sender ID is available
        if (msg.sender && msg.sender.id) {
            ensureColour(msg.sender.id).then(() => {
                const nameElement = messageGroup.querySelector('.message-header .name');
                if (nameElement) {
                    nameElement.innerHTML = nameWithRoleColour(msg.sender);
                }
            });
        }
    }
    
    function updateMessageReactionsDisplay(reactionsBarElement, messageData) {
        reactionsBarElement.innerHTML = ''; 
        if (messageData.reactionCounts && Object.keys(messageData.reactionCounts).length > 0) {
            for (const [emoji, count] of Object.entries(messageData.reactionCounts)) {
                const pill = document.createElement('span');
                pill.className = 'reaction-pill';
                pill.dataset.emoji = emoji; 
                pill.textContent = `${emoji} `;
                const countSpan = document.createElement('span');
                countSpan.className = 'count';
                countSpan.textContent = count;
                pill.appendChild(countSpan);

                if (loggedInUser && loggedInUser.id && messageData.reactionsByEmoji && messageData.reactionsByEmoji[emoji]) {
                     if (messageData.reactionsByEmoji[emoji].some(reactor => reactor.id === loggedInUser.id)) {
                        pill.classList.add('reacted-by-user');
                    }
                }
                pill.onclick = (e) => {
                    e.stopPropagation(); 
                    handleReactionClick(messageData.id, emoji);
                };
                reactionsBarElement.appendChild(pill);
            }
        }
        const addReactionButton = document.createElement('button');
        addReactionButton.className = 'action-btn add-reaction-btn reaction-pill'; 
        addReactionButton.innerHTML = '🙂'; 
        addReactionButton.title = 'Add Reaction';
        addReactionButton.onclick = (event) => {
            event.stopPropagation();
            showEmojiPicker(event.currentTarget, messageData.id);
        };
        reactionsBarElement.appendChild(addReactionButton);
    }

    function updateMessageReactions(msgDto){
      // msgDto contains latest reactions array, including messageId as id
      const messageGroupElement = document.querySelector(`[data-message-id="${msgDto.id}"]`);
      if(!messageGroupElement) {
        console.warn("updateMessageReactions: could not find message element for id", msgDto.id);
        return;
      }
      const reactionsBar = messageGroupElement.querySelector('.reaction-bar');
      if(reactionsBar){
        updateMessageReactionsDisplay(reactionsBar, msgDto); // Use existing helper updateMessageReactionsDisplay
      } else {
        console.warn("updateMessageReactions: could not find reaction bar for message id", msgDto.id);
      }
    }

    function showEmojiPicker(targetButton, messageId) {
        let picker = document.getElementById('emojiPicker');
        if (picker) picker.remove(); 

        picker = document.createElement('div');
        picker.id = 'emojiPicker';
        picker.className = 'emoji-picker';
        
        availableReactions.forEach(emoji => {
            const emojiSpan = document.createElement('span');
            emojiSpan.textContent = emoji;
            emojiSpan.onclick = () => {
                handleReactionClick(messageId, emoji, true); 
                picker.remove();
            };
            picker.appendChild(emojiSpan);
        });

        document.body.appendChild(picker);
        const rect = targetButton.getBoundingClientRect();
        picker.style.left = `${rect.left}px`;
        picker.style.top = `${rect.bottom + 5}px`; 

        setTimeout(() => { 
            document.addEventListener('click', function closePickerOnClickOutside(event) {
                if (picker && !picker.contains(event.target) && event.target !== targetButton && !targetButton.contains(event.target)) {
                    picker.remove();
                    document.removeEventListener('click', closePickerOnClickOutside);
                }
            }, { once: true });
        },0);
    }

    async function handleReactionClick(messageId, emoji, isAddingNewFromPicker = false) {
        if (!stompClient || !stompClient.connected) {
            showToast('Not connected to chat to react.', 'error');
            return;
        }
        if (!loggedInUser.id) {
            showToast('User ID not available. Cannot process reaction.', 'error');
            console.error("loggedInUser.id is null or undefined. Cannot determine if user reacted.");
            return;
        }
        
        const payload = {
            messageId: messageId,
            reactionRequestDTO: { emojiUnicode: emoji }
        };

        let isCurrentlyReactedByUser = false;
        const currentMessageData = findMessageInHistory(messageId);
        if (currentMessageData && currentMessageData.reactionsByEmoji && currentMessageData.reactionsByEmoji[emoji]) {
            isCurrentlyReactedByUser = currentMessageData.reactionsByEmoji[emoji].some(u => u.id === loggedInUser.id);
        }

        if (isCurrentlyReactedByUser && !isAddingNewFromPicker) { 
            stompClient.send('/app/chat.removeReaction', {}, JSON.stringify(payload));
        } else if (!isCurrentlyReactedByUser) { 
            stompClient.send('/app/chat.addReaction', {}, JSON.stringify(payload));
        }
        // If isCurrentlyReactedByUser and isAddingNewFromPicker (clicked same emoji from picker again),
        // it means they want to ensure it's added, or it's a NOP. Backend should handle idempotency.
        // For simplicity, we just send 'addReaction' if it's from picker and not already reacted.
         else if (isCurrentlyReactedByUser && isAddingNewFromPicker) {
            // User clicked an emoji from picker they already reacted with. Could be a NOP or backend handles it.
            console.log("User already reacted with this emoji, clicked from picker.");
        }

        // Send STOMP message for live update (if channel)
        if (currentChatType === 'channel') {
            stompClient.send(`/app/chat.reaction`, {}, JSON.stringify({
                channelId: currentChannelId,
                messageId: messageId, 
                emoji: emoji // ensure this is unicode
            }));
        }
    }
    
    function findMessageInHistory(messageId) {
        const chatId = currentChatType === 'channel' ? currentChannelId : currentDmChatId;
        if (!chatId) return null;
        const cacheKey = `${currentChatType}-${chatId}`;
        const messages = messageHistory.get(cacheKey) || [];
        return messages.find(m => m.id === messageId);
    }


    function setReplyTo(message) {
        replyingToMessage = message;
        updateReplyBar();
        messageInput.focus();
    }

    function updateReplyBar() {
        if (replyingToMessage) {
            replyingToUser.textContent = replyingToMessage.sender?.username || 'Unknown';
            replyingToText.textContent = (replyingToMessage.content || "").substring(0, 50) + ((replyingToMessage.content || "").length > 50 ? '...' : '');
            replyingToBar.classList.remove('hidden');
        } else {
            replyingToBar.classList.add('hidden');
        }
    }
    cancelReplyBtn.addEventListener('click', () => {
        replyingToMessage = null;
        updateReplyBar();
    });

    function connectWebSocket() {
        const chatId = currentChatType === 'channel' ? currentChannelId : currentDmChatId;
        if (!chatId) {
            showToast('No chat selected to connect.', 'warn');
            disconnectChatButton.classList.add('hidden');
            return;
        }

        disconnectWebSocket(); 

        if (!jwtToken) {
            showToast('Cannot connect: Not logged in.', 'error');
            disconnectChatButton.classList.add('hidden');
            return;
        }

        const socket = new SockJS(`${WS_ENDPOINT}?token=${jwtToken}`);
        stompClient = Stomp.over(socket);
        stompClient.debug = (str) => { console.log("STOMP: " + str); };

        showToast('Connecting to chat...', 'info');
        disconnectChatButton.classList.add('hidden');


        stompClient.connect(
            {}, 
            (frame) => { 
                showToast(`Connected to ${currentChatType} ${chatId}`, 'success');
                sendMessageButton.disabled = false;
                disconnectChatButton.classList.remove('hidden');

                const baseTopicPath = currentChatType === 'channel' ? `/topic/channels/${chatId}` : `/topic/dm/${chatId}`;
                
                activeSubscriptions['messages'] = stompClient.subscribe(`${baseTopicPath}/messages`, onMessageReceived);
                activeSubscriptions['messageUpdated'] = stompClient.subscribe(`${baseTopicPath}/messages/updated`, onMessageUpdated);
                activeSubscriptions['messageDeleted'] = stompClient.subscribe(`${baseTopicPath}/messages/deleted`, onMessageDeleted);
                // Subscribe to user profile updates
                activeSubscriptions['userUpdates'] = stompClient.subscribe('/topic/users/updated', (frame) => {
                    const user = JSON.parse(frame.body);
                    console.log("User profile updated:", user);
                    
                    // 1) Update cached user info if it's us
                    if (loggedInUser && user.id === loggedInUser.id) {
                        loggedInUser.avatarUrl = user.avatarUrl;
                        updateUserAvatarDisplay(userProfileBtn, loggedInUser.username, loggedInUser.avatarUrl);
                    }
                    
                    // 2) Update all DM list avatars for this user
                    document.querySelectorAll(`#dm-${user.id} .avatar, .channel-item[data-dm-id] .avatar[data-username="${user.username}"]`).forEach(avatarEl => {
                        const img = avatarEl.querySelector('img');
                        if (img) img.src = absUrl(user.avatarUrl);
                    });
                    
                    // 3) Update all message avatars for this user
                    document.querySelectorAll(`[data-sender='${user.id}'] .avatar`).forEach(avatarEl => {
                        const img = avatarEl.querySelector('img');
                        if (img) img.src = absUrl(user.avatarUrl);
                    });
                });
                // Subscribe to typing indicators
                activeSubscriptions['typing'] = stompClient.subscribe(`${baseTopicPath}/typing`, (frame) => {
                    const typingData = JSON.parse(frame.body);
                    // Don't show our own typing indicator
                    if (typingData.userId === loggedInUser.id) return;
                    
                    // Store the typing user with timestamp and name
                    typingUsers.set(typingData.userId, {
                        time: Date.now(),
                        name: typingData.username
                    });
                });

                stompClient.subscribe('/user/queue/errors', (errorFrame) => {
                    try {
                        const errorPayload = JSON.parse(errorFrame.body);
                        showToast(`Server Error: ${errorPayload.details || errorPayload.error}`, 'error');
                    } catch (e) { showToast(`Server Error: ${errorFrame.body}`, 'error'); }
                });
            },
            (error) => { 
                console.error('STOMP Connection Error:', error);
                showToast(`WebSocket Connection Error: ${error.headers ? error.headers.message : error}`, 'error');
                sendMessageButton.disabled = true;
                disconnectChatButton.classList.add('hidden');
            }
        );
    }

    function disconnectWebSocket() {
        for (const key in activeSubscriptions) {
            if (activeSubscriptions[key]) {
                try { activeSubscriptions[key].unsubscribe(); } catch (e) { console.warn("Error unsubscribing:", key, e); }
                activeSubscriptions[key] = null;
            }
        }
        if (stompClient && stompClient.connected) {
            stompClient.disconnect(() => {
                showToast('Disconnected from chat.', 'info');
            });
        }
        stompClient = null; 
        sendMessageButton.disabled = true;
        disconnectChatButton.classList.add('hidden');
    }
    disconnectChatButton.addEventListener('click', disconnectWebSocket);

    // subscribe for live reaction updates
    function subscribeReactions(chanId) {
      if(activeSubscriptions.reacts) stompClient.unsubscribe(activeSubscriptions.reacts);
      activeSubscriptions.reacts = stompClient.subscribe(
        `${WS_REACTIONS_DEST}/${chanId}/reactions`,
        ({body}) => updateMessageReactions(JSON.parse(body))
      ).id;
    }

    function onMessageReceived(payload) {
        console.log("Raw message received (main topic):", payload);
        try {
            const message = JSON.parse(payload.body);
            console.log("Parsed message (main topic):", message);
            displayMessage(message); // Append new message
            
            const cacheKey = `${message.channelId ? 'channel' : 'dm'}-${message.channelId || message.directMessageChatId}`;
            const existingMessages = messageHistory.get(cacheKey) || [];
            if (!existingMessages.find(m => m.id === message.id)) { 
                const updatedMessages = [...existingMessages, message].sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt));
                messageHistory.set(cacheKey, updatedMessages);
            }

        } catch (e) {
            console.error("Error parsing incoming message (main topic):", e, payload.body);
            showToast("Received an invalid message from server.", "error");
        }
    }
    
    function onMessageUpdated(payload) { // For reaction updates, edits, etc.
        console.log("Raw message updated:", payload);
        try {
            const updatedMessage = JSON.parse(payload.body); 
            console.log("Parsed updated message:", updatedMessage);
            
            const existingMsgElement = document.querySelector(`.message-group[data-message-id="${updatedMessage.id}"]`);
            if (existingMsgElement) {
                // More granular update: just update reactions or content if edited
                const reactionsBar = existingMsgElement.querySelector('.reaction-bar');
                if (reactionsBar) updateMessageReactionsDisplay(reactionsBar, updatedMessage);
                
                if (updatedMessage.edited) {
                    const contentEl = existingMsgElement.querySelector('.message-content');
                    if(contentEl) contentEl.textContent = updatedMessage.content || '';
                    const headerEl = existingMsgElement.querySelector('.message-header');
                    if(headerEl && !headerEl.innerHTML.includes('(edited)')) {
                        headerEl.innerHTML += ' <span class="text-xs text-[var(--text-muted)]">(edited)</span>';
                    }
                }
                // Update repliedTo if that can change (unlikely for existing message)
                const repliedToElement = existingMsgElement.querySelector('.replied-to-message');
                if (updatedMessage.repliedTo && updatedMessage.repliedTo.senderUsername && updatedMessage.repliedTo.contentSnippet) {
                    if (!repliedToElement) { // Add if it wasn't there before (unlikely for an update)
                        const newRepliedToDiv = document.createElement('div');
                        newRepliedToDiv.className = 'replied-to-message';
                        newRepliedToDiv.innerHTML = `
                            <span class="icon mr-1">↩️</span> Replying to <strong>${updatedMessage.repliedTo.senderUsername}</strong>:
                            <span class="truncate inline-block max-w-xs italic">"${updatedMessage.repliedTo.contentSnippet}..."</span>`;
                        existingMsgElement.querySelector('.message-body').insertBefore(newRepliedToDiv, existingMsgElement.querySelector('.message-header'));
                    } else { // Update existing
                         repliedToElement.innerHTML = `
                            <span class="icon mr-1">↩️</span> Replying to <strong>${updatedMessage.repliedTo.senderUsername}</strong>:
                            <span class="truncate inline-block max-w-xs italic">"${updatedMessage.repliedTo.contentSnippet}..."</span>`;
                    }
                } else if (repliedToElement) {
                    repliedToElement.remove(); // Remove if reply info is gone
                }


            } else { // If message wasn't on screen, just add it (less likely for an update)
                displayMessage(updatedMessage);
            }

            // Update cache
            const cacheKey = `${updatedMessage.channelId ? 'channel' : 'dm'}-${updatedMessage.channelId || updatedMessage.directMessageChatId}`;
            let messages = messageHistory.get(cacheKey) || [];
            const msgIndex = messages.findIndex(m => m.id === updatedMessage.id);
            if (msgIndex > -1) {
                messages[msgIndex] = updatedMessage;
            } else {
                messages.push(updatedMessage); // Should ideally already be there if it's an update
            }
            messages.sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt));
            messageHistory.set(cacheKey, messages);

        } catch (e) {
            console.error("Error parsing updated message:", e, payload.body);
        }
    }

    function onMessageDeleted(payload) {
        console.log("Raw message deleted:", payload);
        try {
            const deleteInfo = JSON.parse(payload.body); 
            console.log("Parsed deleted info:", deleteInfo);
            const msgElement = document.querySelector(`.message-group[data-message-id="${deleteInfo.messageId}"]`);
            if (msgElement) {
                msgElement.remove();
                showToast(`Message ${deleteInfo.messageId} deleted.`, 'info');
            }
            const cacheKey = `${deleteInfo.channelId ? 'channel' : 'dm'}-${deleteInfo.channelId || deleteInfo.directMessageChatId}`;
            let messages = messageHistory.get(cacheKey) || [];
            messages = messages.filter(m => m.id !== deleteInfo.messageId);
            messageHistory.set(cacheKey, messages);

        } catch (e) {
            console.error("Error parsing delete info:", e, payload.body);
        }
    }


    sendMessageButton.addEventListener('click', handleSendMessage);
    messageInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSendMessage();
        }
    });

    async function handleSendMessage() {
        const content = messageInput.value.trim();
        if ((!content && uploadedFiles.length === 0) || !stompClient || !stompClient.connected) {
            if (!stompClient || !stompClient.connected) showToast('Not connected to chat.', 'error');
            return;
        }

        const messagePayload = {
            content: content,
            parentMessageId: replyingToMessage ? replyingToMessage.id : null
        };

        if (currentChatType === 'channel') {
            messagePayload.channelId = currentChannelId;
        } else if (currentChatType === 'dm') {
            messagePayload.directMessageChatId = currentDmChatId;
        } else {
            showToast('No active chat selected.', 'error');
            return;
        }

        if (uploadedFiles.length > 0) {
            const formData = new FormData();
            formData.append('sendMessageDTO', new Blob([JSON.stringify(messagePayload)], { type: 'application/json' }));
            uploadedFiles.forEach(file => formData.append('files', file));

            try {
                const response = await api(`${API_BASE_URL}/messages`, {
                    method: 'POST',
                    body: formData,
                });
                if (!response.ok) {
                    let errorData;
                    try {errorData = await response.json();} catch(e){ errorData = {message: await response.text()}}
                    throw new Error(errorData.message || `Failed to send message with files: ${response.status}`);
                }
                showToast('Message with files sent!', 'success');
            } catch (error) {
                console.error('Error sending message with files:', error);
                showToast(`Error: ${error.message}`, 'error');
            }
        } else { 
            stompClient.send('/app/chat.sendMessage', {}, JSON.stringify(messagePayload));
        }

        messageInput.value = '';
        uploadedFiles = [];
        fileUploadPreviewContainer.innerHTML = '';
        replyingToMessage = null;
        updateReplyBar();
        messageInput.style.height = 'auto'; 
    }
    
    messageInput.addEventListener('input', () => {
        messageInput.style.height = 'auto';
        messageInput.style.height = (messageInput.scrollHeight) + 'px';
        sendTyping();
    });

    // Typing indicator functions
    function renderTyping() {
        const now = Date.now();
        // Clear users who haven't typed for more than 3 seconds
        typingUsers.forEach((data, uid) => { 
            if (now - data.time > 3000) typingUsers.delete(uid); 
        });
        
        // Get array of usernames still typing
        const names = Array.from(typingUsers.values()).map(data => data.name);
        
        // Update the typing indicator message
        if (names.length > 0) {
            typingIndicator.textContent = names.join(', ') + (names.length > 1 ? ' are' : ' is') + ' typing…';
        } else {
            typingIndicator.textContent = '';
        }
    }
    
    // Function to send typing indicator, debounced
    const sendTyping = debounce(() => {
        if (!currentChatType || (!currentChannelId && !currentDmChatId) || !stompClient || !stompClient.connected) return;
        
        const payload = {
            chatId: currentChatType === 'channel' ? currentChannelId : currentDmChatId,
            direct: currentChatType === 'dm'
        };
        
        stompClient.send('/app/chat.typing', {}, JSON.stringify(payload));
    }, 400);
    
    // Update typing indicators periodically
    setInterval(renderTyping, 800);

    // --- FILE ATTACHMENT HANDLING ---
    attachFileButton.addEventListener('click', () => fileUploadInput.click());
    fileUploadInput.addEventListener('change', (event) => {
        for (const file of event.target.files) {
            if (uploadedFiles.length < 5) { 
                uploadedFiles.push(file);
                renderFileUploadPreview(file);
            } else {
                showToast('Maximum 5 files allowed.', 'warn');
                break;
            }
        }
        fileUploadInput.value = ''; 
    });

    function renderFileUploadPreview(file) {
        const previewDiv = document.createElement('div');
        previewDiv.className = 'upload-thumbnail';
        
        if (file.type.startsWith('image/')) {
            const img = document.createElement('img');
            img.src = URL.createObjectURL(file);
            img.onload = () => URL.revokeObjectURL(img.src);
            previewDiv.appendChild(img);
        } else {
            previewDiv.textContent = file.name.split('.').pop().toUpperCase().substring(0, 4);
        }

        const removeBtn = document.createElement('button');
        removeBtn.className = 'remove-upload';
        removeBtn.innerHTML = '&times;';
        removeBtn.onclick = () => {
            uploadedFiles = uploadedFiles.filter(f => f !== file);
            previewDiv.remove();
        };
        previewDiv.appendChild(removeBtn);
        fileUploadPreviewContainer.appendChild(previewDiv);
    }

    // --- PROFILE MODAL ---
    userProfileBtn.addEventListener('click', () => {
        profileDisplayNameInput.value = loggedInUser.username; 
        profileBioInput.value = loggedInUser.bio || '';
        updateUserAvatarDisplay(profileModalAvatar, loggedInUser.username, loggedInUser.avatarUrl);
        profileModalStatus.textContent = '';
        profileModalContainer.classList.add('show');
    });

    changeAvatarButton.addEventListener('click', () => avatarFileInput.click());
    avatarFileInput.addEventListener('change', (event) => {
        const file = event.target.files[0];
        if (file && file.type.startsWith('image/')) {
            const reader = new FileReader();
            reader.onload = (e) => {
                // Store the file for upload, but don't use data URL for display
                profileModalAvatar.innerHTML = `<img src="${e.target.result}" alt="avatar preview" class="w-full h-full object-cover rounded-full"/>`;
            };
            reader.readAsDataURL(file);
        } else if (file) {
            showToast('Please select an image file.', 'warn');
        }
    });

    cancelProfileButton.addEventListener('click', () => {
        profileModalContainer.classList.remove('show');
        avatarFileInput.value = ''; 
        // Reset avatar preview to current avatar
        updateUserAvatarDisplay(profileModalAvatar, loggedInUser.username, loggedInUser.avatarUrl);
    });

    saveProfileButton.addEventListener('click', async () => {
        const newDisplayName = profileDisplayNameInput.value.trim();
        const newBio = profileBioInput.value.trim();

        if (!newDisplayName) {
            profileModalStatus.textContent = 'Display name cannot be empty.';
            profileModalStatus.className = 'text-sm text-center text-red-500 h-4';
            return;
        }
        profileModalStatus.textContent = 'Saving...';
        profileModalStatus.className = 'text-sm text-center text-yellow-500 h-4';
        
        try {
          console.log("=== PROFILE UPDATE START ===");
          const formData = new FormData();
          
          if (newDisplayName) formData.append('username', newDisplayName);
          if (newBio) formData.append('bio', newBio);
          if (avatarFileInput.files[0]) formData.append('avatar', avatarFileInput.files[0]);
          
          // Log if we're uploading a file
          if (avatarFileInput.files[0]) { 
            console.log("Uploading avatar file:", avatarFileInput.files[0].name, avatarFileInput.files[0].type, avatarFileInput.files[0].size);
          } else {
            console.log("No avatar file selected for upload");
          }
          
          // Ensure token is valid before making the request
          console.log("Checking token validity before profile update...");
          const isTokenValid = await ensureValidToken();
          console.log("Token validation result:", isTokenValid);
          
          if (!isTokenValid) {
            profileModalStatus.textContent = 'Session expired. Please log in again.';
            profileModalStatus.className = 'text-sm text-center text-red-500 h-4';
            console.log("Token validation failed - not proceeding with request");
            return;
          }
          
          console.log("Token is valid. Sending profile update request...");
          // console.log("Using JWT token (first 10 chars for security):", jwtToken ? jwtToken.substring(0, 10) + "..." : "null");
          
          const response = await api(`${API_BASE_URL}/users/me/profile`, {
            method: 'PUT',
            body: formData
          });
          
          console.log("Profile update response status:", response.status, response.statusText);
          console.log("Profile update response headers:", 
              Array.from(response.headers.entries())
                .map(([key, value]) => `${key}: ${value}`)
                .join(', ')
          );
          
          if (response.type === 'opaqueredirect') {
              console.error("Received opaque redirect response - server is redirecting");
              throw new Error("Server is redirecting the request - likely an authentication issue");
          }
          
          if (!response.ok) {
            let errorMessage = `Failed to save profile: ${response.status}`;
            let errorData = null;
            
            try {
              const errorText = await response.text();
              console.error("Error response body:", errorText);
              
              if (response.status === 401) {
                // Force logout on authentication failure but don't redirect yet
                console.log("Received 401 - handling authentication failure");
                profileModalStatus.textContent = 'Authentication failed. Please try logging in again.';
                profileModalStatus.className = 'text-sm text-center text-red-500 h-4';
                throw new Error("Authentication failed. Please log in again.");
              }
              
              try { 
                errorData = JSON.parse(errorText); 
                errorMessage = errorData.message || errorData.error || errorMessage; 
              } catch(e) {
                errorMessage = errorText || errorMessage;
              }
            } catch(e) {
              console.error("Error parsing error response:", e);
            }
            
            throw new Error(errorMessage);
          }
          
          // Check if the response is actually JSON
          let responseText;
          try {
            responseText = await response.text();
            console.log("Raw response text:", responseText.substring(0, 100) + (responseText.length > 100 ? '...' : ''));
          } catch (error) {
            console.error("Error reading response body:", error);
            throw new Error("Could not read server response");
          }
          
          let updatedUser;
          try {
            updatedUser = JSON.parse(responseText);
            console.log("Parsed response as JSON:", updatedUser);
          } catch (error) {
            console.error("Error parsing JSON response:", error);
            
            // Check if the response contains HTML (likely a redirect to login page)
            if (responseText.includes("<html") || responseText.includes("<!DOCTYPE")) {
              console.error("Received HTML response instead of JSON - likely a redirect to login page");
              const parser = new DOMParser();
              const htmlDoc = parser.parseFromString(responseText, 'text/html');
              const title = htmlDoc.querySelector('title')?.textContent;
              throw new Error(`Server returned HTML (${title || 'unknown page'}) instead of user data. Try logging in again.`);
            }
            
            throw new Error("Server response was not valid JSON: " + error.message);
          }
          
          // Debug check the avatar URL
          console.log("Server returned user data:", updatedUser);
          
          if (!updatedUser.avatarUrl) {
            console.warn("Server returned null or empty avatarUrl");
            showToast("Warning: Server didn't return an avatar URL", "warn");
          } else {
            console.log("Server returned avatarUrl:", updatedUser.avatarUrl);
            debugCheckAvatarUrl(updatedUser.avatarUrl);
            
            // Create a test image to verify the URL works
            const testImg = new Image();
            testImg.onload = () => {
              console.log("✅ Avatar image loaded successfully from server URL");
              // Show the image in a toast for verification
              const toast = document.createElement('div');
              toast.className = 'toast-notification';
              toast.style.backgroundColor = 'var(--success)';
              toast.innerHTML = `
                <div style="display: flex; align-items: center; gap: 10px;">
                  <img src="${absUrl(updatedUser.avatarUrl)}" style="width: 40px; height: 40px; border-radius: 50%; object-fit: cover;" />
                  <span>Avatar updated successfully!</span>
                </div>
              `;
              document.body.appendChild(toast);
              toast.addEventListener('animationend', () => toast.remove());
            };
            testImg.onerror = () => {
              console.error("❌ Failed to load avatar image from server URL:", updatedUser.avatarUrl);
              
              // Display a diagnostic message
              const diagnosticMessage = `
                <div style="text-align: left; margin-top: 10px;">
                  <p>Avatar URL: ${updatedUser.avatarUrl}</p>
                  <p>Try accessing these URLs directly:</p>
                  <ul>
                    <li><a href="${absUrl(updatedUser.avatarUrl)}" target="_blank">Direct URL</a></li>
                    <li><a href="/check-uploads.html" target="_blank">Check Uploads Directory</a></li>
                    <li><a href="/uploads/" target="_blank">Browse Uploads Directory</a></li>
                  </ul>
                </div>
              `;
              
              showToast(`Error: Avatar URL returned by server is invalid. ${diagnosticMessage}`, "error");
            };
            testImg.src = absUrl(updatedUser.avatarUrl);
          }
          
          // Update all user info
          loggedInUser.username = updatedUser.username; 
          loggedInUser.avatarUrl = updatedUser.avatarUrl;
          loggedInUser.bio = updatedUser.bio;

          // Update all avatar displays
          updateUserAvatarDisplay(userProfileBtn, loggedInUser.username, loggedInUser.avatarUrl);
          
          // Update avatar in any open chats
          document.querySelectorAll('.message-group .avatar').forEach(avatar => {
              const messageGroup = avatar.closest('.message-group');
              if (messageGroup && messageGroup.dataset.senderUsername === loggedInUser.username) {
                  updateUserAvatarDisplay(avatar, loggedInUser.username, loggedInUser.avatarUrl);
              }
          });

          // Update avatar in server list if user is in any servers
          document.querySelectorAll('.server-icon').forEach(serverIcon => {
              if (serverIcon.dataset.username === loggedInUser.username) {
                  updateUserAvatarDisplay(serverIcon, loggedInUser.username, loggedInUser.avatarUrl);
              }
          });

          // Update avatar in DM list
          document.querySelectorAll('.channel-item[data-dm-id] .avatar').forEach(avatar => {
              const dmItem = avatar.closest('.channel-item');
              if (dmItem && dmItem.dataset.username === loggedInUser.username) {
                  updateUserAvatarDisplay(avatar, loggedInUser.username, loggedInUser.avatarUrl);
              }
          });

          showToast('Profile saved successfully!', 'success');
          profileModalContainer.classList.remove('show');
          profileModalStatus.textContent = '';
        } catch (error) {
          console.error("Save profile error:", error);
          profileModalStatus.textContent = `Error: ${error.message}`;
          profileModalStatus.className = 'text-sm text-center text-red-500 h-4';
        } finally {
            avatarFileInput.value = ''; 
        }
    });

    // --- INITIALIZATION ---
    document.addEventListener('DOMContentLoaded', () => {
        loginUsernameInput.value = 'userone'; // Default for easier testing
        loginPasswordInput.value = 'password123'; // Default for easier testing
        
        sendMessageButton.disabled = true;
        disconnectChatButton.classList.add('hidden'); 
    });

    // Add this function to check and refresh token if needed
    async function ensureValidToken() {
        console.log("ensureValidToken called");
        
        if (!jwtToken) {
            console.error("No JWT token available");
            // showToast("You're not logged in. Please log in first.", "error"); // api wrapper will handle this if called
            return false;
        }
        
        // Check if token is expired or about to expire (within 5 minutes)
        try {
            console.log("Decoding token...");
            const tokenParts = jwtToken.split('.');
            console.log("Token has", tokenParts.length, "parts");
            
            if (tokenParts.length !== 3) {
                console.error("Token has incorrect format - not a valid JWT");
                throw new Error("Invalid token format");
            }
            
            const payload = JSON.parse(atob(tokenParts[1]));
            console.log("Token payload:", payload);
            
            const expTime = payload.exp * 1000; // Convert to milliseconds
            const currentTime = new Date().getTime();
            
            console.log("Current time:", new Date(currentTime).toLocaleString());
            console.log("Token expiry:", new Date(expTime).toLocaleString());
            console.log("Time until expiry:", (expTime - currentTime) / 1000, "seconds");
            
            // If token is expired or about to expire
            if (!expTime || (expTime - currentTime < 300000)) {
                console.log("Token is expired or about to expire, refreshing...");
                try {
                    const response = await api(`${API_BASE_URL}/auth/refresh`, { // Use api()
                        method: 'POST',
                        // redirect: 'manual' // api() handles this
                    });
                    
                    console.log("Refresh token response status:", response.status);
                    
                    // if (response.type === 'opaqueredirect') { // api() handles this
                    //     console.error("Refresh token request was redirected");
                    //     throw new Error("Token refresh failed: request redirected");
                    // }
                    
                    if (!response.ok) {
                        const errorText = await response.text();
                        console.error("Token refresh failed:", response.status, errorText);
                        throw new Error(`Failed to refresh token: ${response.status} ${errorText}`);
                    }
                    
                    const newToken = await response.text();
                    console.log("New token received, length:", newToken.length);
                    
                    // Validate the new token before accepting it
                    if (!newToken || newToken.trim() === '' || !newToken.includes('.')) {
                        console.error("Received invalid token from refresh endpoint:", newToken);
                        throw new Error("Received invalid token from server");
                    }
                    
                    // Update token
                    jwtToken = newToken;
                    
                    // Update token expiry time
                    try {
                        const newPayload = JSON.parse(atob(jwtToken.split('.')[1]));
                        jwtTokenExpiry = newPayload.exp * 1000; // Convert to milliseconds
                        console.log("Token refreshed successfully, new expiry:", new Date(jwtTokenExpiry).toLocaleString());
                        return true;
                    } catch (parseError) {
                        console.error("Error parsing new token payload:", parseError);
                        throw new Error("Invalid new token format");
                    }
                } catch (error) {
                    console.error("Error refreshing token:", error);
                    showToast("Your session has expired. Please log in again.", "error");
                    // Don't redirect here, just return false
                    return false;
                }
            }
            console.log("Token is valid and not about to expire");
            return true;
        } catch (error) {
            console.error("Error parsing token:", error);
            showToast("Invalid token format. Please log in again.", "error");
            // Don't redirect here, just return false
            return false;
        }
    }

    // Add friends functionality
    const friendsTab = $('#friendsTab');
    const friendsPane = $('#friendsPane');
    const friendsList = $('#friendsList');
    const pendingRequestsList = $('#pendingRequestsList');
    const friendSearch = $('#friendSearch');

    friendsTab.addEventListener('click', toggleFriendsPane);

    async function toggleFriendsPane() {
        const isVisible = !friendsPane.classList.contains('hidden');
        if (isVisible) {
            friendsPane.classList.add('hidden');
        } else {
            friendsPane.classList.remove('hidden');
            await loadFriendsData();
            await loadPendingRequests();
        }
    }

    async function loadFriendsData() {
        if (!jwtToken) return;
        friendsList.innerHTML = '<p class="text-center text-[var(--text-muted)] p-4">Loading friends...</p>';
        
        try {
            const response = await api(`${API_BASE_URL}/friends`, {});
            
            if (!response.ok) {
                throw new Error(`Failed to fetch friends: ${response.status} ${await response.text()}`);
            }
            
            const friends = await response.json();
            renderFriendsList(friends);
        } catch (error) {
            console.error('Error loading friends:', error);
            friendsList.innerHTML = `<p class="text-center text-[var(--danger)] p-4">Error: ${error.message}</p>`;
        }
    }

    async function loadPendingRequests() {
        if (!jwtToken) return;
        pendingRequestsList.innerHTML = '<p class="text-center text-[var(--text-muted)] p-2">Loading requests...</p>';
        
        try {
            const response = await api(`${API_BASE_URL}/friends/requests`, {});
            
            if (!response.ok) {
                throw new Error(`Failed to fetch pending requests: ${response.status} ${await response.text()}`);
            }
            
            const requests = await response.json();
            renderPendingRequests(requests);
        } catch (error) {
            console.error('Error loading pending requests:', error);
            pendingRequestsList.innerHTML = `<p class="text-center text-[var(--danger)] p-2">Error: ${error.message}</p>`;
        }
    }

    function renderFriendsList(friends) {
        friendsList.innerHTML = '';
        
        if (!friends || friends.length === 0) {
            friendsList.innerHTML = '<p class="text-center text-[var(--text-muted)] p-4">No friends yet. Search to add friends!</p>';
            return;
        }
        
        friends.forEach(friend => {
            const friendEl = document.createElement('div');
            friendEl.className = 'dm-row';
            
            const avatarDiv = document.createElement('div');
            avatarDiv.className = 'avatar';
            if (friend.avatarUrl) {
                avatarDiv.innerHTML = `<img src="${absUrl(friend.avatarUrl)}" alt="${friend.username}" class="avatar">`;
            } else {
                createAvatarPlaceholder(avatarDiv, friend.username);
            }
            
            const nameSpan = document.createElement('span');
            nameSpan.textContent = friend.username;
            
            const messageBtn = document.createElement('button');
            messageBtn.className = 'btn';
            messageBtn.textContent = 'Message';
            messageBtn.onclick = () => startDMWithFriend(friend);
            
            friendEl.appendChild(avatarDiv);
            friendEl.appendChild(nameSpan);
            friendEl.appendChild(messageBtn);
            
            friendsList.appendChild(friendEl);
        });
    }

    function renderPendingRequests(requests) {
        pendingRequestsList.innerHTML = '';
        
        if (!requests || requests.length === 0) {
            pendingRequestsList.innerHTML = '<p class="text-center text-[var(--text-muted)] p-2">No pending requests</p>';
            return;
        }
        
        requests.forEach(request => {
            const requestEl = document.createElement('div');
            requestEl.className = 'dm-row';
            
            const avatarDiv = document.createElement('div');
            avatarDiv.className = 'avatar';
            if (request.sender.avatarUrl) {
                avatarDiv.innerHTML = `<img src="${absUrl(request.sender.avatarUrl)}" alt="${request.sender.username}" class="avatar">`;
            } else {
                createAvatarPlaceholder(avatarDiv, request.sender.username);
            }
            
            const nameSpan = document.createElement('span');
            nameSpan.textContent = `${request.sender.username} wants to be friends`;
            
            const acceptBtn = document.createElement('button');
            acceptBtn.className = 'btn';
            acceptBtn.textContent = 'Accept';
            acceptBtn.onclick = () => respondToFriendRequest(request.id, true);
            
            const declineBtn = document.createElement('button');
            declineBtn.className = 'secondary';
            declineBtn.textContent = 'Decline';
            declineBtn.onclick = () => respondToFriendRequest(request.id, false);
            
            requestEl.appendChild(avatarDiv);
            requestEl.appendChild(nameSpan);
            requestEl.appendChild(acceptBtn);
            requestEl.appendChild(declineBtn);
            
            pendingRequestsList.appendChild(requestEl);
        });
    }

    async function respondToFriendRequest(requestId, accept) {
        try {
            const response = await api(`${API_BASE_URL}/friends/requests/${requestId}?accept=${accept}`, {
                method: 'PUT',
            });
            
            if (!response.ok) {
                throw new Error(`Failed to respond to request: ${response.status} ${await response.text()}`);
            }
            
            showToast(`Friend request ${accept ? 'accepted' : 'declined'}`, 'success');
            
            // Reload data
            await loadFriendsData();
            await loadPendingRequests();
            
        } catch (error) {
            console.error('Error responding to friend request:', error);
            showToast(`Error: ${error.message}`, 'error');
        }
    }

    friendSearch.addEventListener('input', debounce(searchUsers, 500));

    async function searchUsers() {
        const query = friendSearch.value.trim();
        if (!query || query.length < 2) return;
        
        try {
            const response = await api(`${API_BASE_URL}/friends/search?q=${encodeURIComponent(query)}`, {});
            
            if (!response.ok) {
                throw new Error(`Search failed: ${response.status} ${await response.text()}`);
            }
            
            const users = await response.json();
            renderSearchResults(users);
        } catch (error) {
            console.error('Search error:', error);
            showToast(`Search error: ${error.message}`, 'error');
        }
    }

    function renderSearchResults(users) {
        friendsList.innerHTML = '';
        
        if (!users || users.length === 0) {
            friendsList.innerHTML = '<p class="text-center text-[var(--text-muted)] p-4">No users found</p>';
            return;
        }
        
        users.forEach(user => {
            const userEl = document.createElement('div');
            userEl.className = 'dm-row';
            
            const avatarDiv = document.createElement('div');
            avatarDiv.className = 'avatar';
            if (user.avatarUrl) {
                avatarDiv.innerHTML = `<img src="${absUrl(user.avatarUrl)}" alt="${user.username}" class="avatar">`;
            } else {
                createAvatarPlaceholder(avatarDiv, user.username);
            }
            
            const nameSpan = document.createElement('span');
            nameSpan.textContent = user.username;
            
            const addBtn = document.createElement('button');
            addBtn.className = 'btn';
            addBtn.textContent = 'Add Friend';
            addBtn.onclick = () => sendFriendRequest(user.id);
            
            userEl.appendChild(avatarDiv);
            userEl.appendChild(nameSpan);
            userEl.appendChild(addBtn);
            
            friendsList.appendChild(userEl);
        });
    }

    async function sendFriendRequest(userId) {
        try {
            const response = await api(`${API_BASE_URL}/friends/requests`, {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ receiverId: userId })
            });
            
            if (!response.ok) {
                throw new Error(`Failed to send friend request: ${response.status} ${await response.text()}`);
            }
            
            showToast('Friend request sent!', 'success');
        } catch (error) {
            console.error('Error sending friend request:', error);
            showToast(`Error: ${error.message}`, 'error');
        }
    }

    async function startDMWithFriend(friend) {
        // Close friends pane
        friendsPane.classList.add('hidden');
        
        showToast(`Starting chat with ${friend.username}...`, 'info');
        
        try {
            const dmChat = await openOrCreateDm(friend.id);
            if (dmChat) {
                openDmChat(dmChat);
            }
        } catch (error) {
            console.error('Error starting DM chat:', error);
            showToast(`Error: ${error.message}`, 'error');
        }
    }

    // Simple debounce function for search
    function debounce(func, delay) {
        let timeout;
        return function() {
            const context = this;
            const args = arguments;
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(context, args), delay);
        };
    }

    // --- SERVER SETTINGS MODAL (New Section) ---
    const serverSettingsModal = $('#serverSettingsModal'); // Placeholder, ensure this ID matches your HTML
    const openServerSettingsBtn = $('#openServerSettingsBtn'); // Placeholder
    const closeServerSettingsBtn = $('#closeServerSettingsBtn'); // Placeholder
    const pickServerIconBtn = $('#pickServerIconBtn'); // Placeholder
    const newServerIcon           = $('#newServerIcon');
    const saveServerInfoBtn = $('#saveServerInfoBtn'); // Placeholder
    const createChannelBtn = $('#createChannelBtn'); // Placeholder
    const channelsAdminList = $('#channelsAdminList'); // Placeholder for list of channels in admin
    const serverSettingsNameInput = $('#serverSettingsNameInput');

    if (openServerSettingsBtn) {
        openServerSettingsBtn.onclick = () => {
            populateServerSettings();
            if(serverSettingsModal) serverSettingsModal.classList.remove('hidden');
        };
    }

    if (closeServerSettingsBtn && serverSettingsModal) {
        closeServerSettingsBtn.onclick = () => serverSettingsModal.classList.add('hidden');
    }

    if (pickServerIconBtn && newServerIcon) {
        pickServerIconBtn.onclick = () => newServerIcon.click();
    }

    if (saveServerInfoBtn) {
        saveServerInfoBtn.onclick  = saveServerInfo;
    }

    if (createChannelBtn) {
        createChannelBtn.onclick   = createChannel;
    }

    if (channelsAdminList) {
        channelsAdminList.onclick  = handleChannelAdminClicks;
    }

    async function populateServerSettings() {
        if (!window.currentServerData || !serverSettingsModal) {
            showToast("Open a server first.", "error");
            return;
        }
        const serverDetails = window.currentServerData;   // already has channels
        if(serverSettingsNameInput) serverSettingsNameInput.value = serverDetails.name;
        
        // Populate channels list for admin
        if (channelsAdminList) {
            channelsAdminList.innerHTML = ''; // Clear previous
            if (serverDetails.channels && serverDetails.channels.length > 0) {
                serverDetails.channels.forEach(channel => {
                    const li = document.createElement('li');
                    li.className = 'flex justify-between items-center p-1 hover:bg-[var(--background-modifier-hover)]';
                    li.innerHTML = `<span># ${channel.name}</span> <button data-channel-id="${channel.id}" class="text-red-500 text-xs p-1 rounded hover:bg-red-700 hover:text-white">Delete</button>`;
                    channelsAdminList.appendChild(li);
                });
            } else {
                channelsAdminList.innerHTML = '<p class="text-xs text-[var(--text-muted)] p-1">No channels in this server.</p>';
            }
        }
    }

    async function saveServerInfo() {
        if (!currentServerId || !serverSettingsNameInput) return;
        const newName = serverSettingsNameInput.value.trim();
        const iconFile = newServerIcon && newServerIcon.files[0] ? newServerIcon.files[0] : null;

        if (!newName) {
            showToast("Server name cannot be empty.", "error");
            return;
        }

        try {
            // Update name first
            const nameResponse = await api(`${API_BASE_URL}/servers/${currentServerId}/name`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: newName })
            });
            if (!nameResponse.ok) throw new Error(`Failed to update server name: ${await nameResponse.text()}`);
            showToast("Server name updated!", "success");

            // Update icon if selected (Part 4 fix)
            if (iconFile) {
              const fd = new FormData();
              fd.append('icon', iconFile);
              const res = await api(`${API_BASE_URL}/servers/${currentServerId}/icon`, {
                method: 'PUT',
                body: fd                    // headers added automatically by api() for FormData
              });
              if (!res.ok) throw new Error(await res.text());
              showToast("Server icon updated!", "success");
            }
            await loadServersAndDMs(); // Refresh server list
            if (serverSettingsModal) serverSettingsModal.classList.add('hidden'); 
        } catch (error) {
            showToast(`Error saving server info: ${error.message}`, 'error');
            console.error("Save server info error:", error);
        }
    }

    async function createChannel() {
        if (!currentServerId) return;
        const channelName = prompt("Enter name for the new text channel:");
        if (!channelName || channelName.trim() === "") return;

        try {
            const response = await api(`${API_BASE_URL}/servers/${currentServerId}/channels`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: channelName.trim(), type: 'TEXT' })
            });
            if (!response.ok) throw new Error(`Failed to create channel: ${await response.text()}`);
            showToast(`Channel "${channelName}" created!`, 'success');
            await loadServersAndDMs(); // Refresh list
            // Optionally, re-populate settings if modal is open
            if (serverSettingsModal && !serverSettingsModal.classList.contains('hidden')) {
                 await populateServerSettings();
            }
        } catch (error) {
            showToast(`Error creating channel: ${error.message}`, 'error');
            console.error("Create channel error:", error);
        }
    }

    async function handleChannelAdminClicks(event) {
        if (event.target.tagName === 'BUTTON' && event.target.dataset.channelId) {
            const channelId = event.target.dataset.channelId;
            const channelName = event.target.previousElementSibling.textContent; 
            if (confirm(`Are you sure you want to delete channel "${channelName}"?`)) {
                try {
                    const response = await api(`${API_BASE_URL}/channels/${channelId}`, {
                        method: 'DELETE'
                    });
                    if (!response.ok) throw new Error(`Failed to delete channel: ${await response.text()}`);
                    showToast(`Channel "${channelName}" deleted.`, 'success');
                    await loadServersAndDMs(); // Refresh list
                    // Re-populate settings
                    await populateServerSettings(); 
                } catch (error) {
                    showToast(`Error deleting channel: ${error.message}`, 'error');
                    console.error("Delete channel error:", error);
                }
            }
        }
    }

    // Ensure this is the correct closing for DOMContentLoaded
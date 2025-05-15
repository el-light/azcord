let peerConnection;
let stompClient;

let serverId = null;
let channelId = null;

document.getElementById('leaveButton').addEventListener('click', () => {
    if (peerConnection) peerConnection.close();
    if (stompClient) stompClient.disconnect();
    alert("You left the call.");
    window.location.href = '/';
});

function startCall() {
    const urlParams = new URLSearchParams(window.location.search);
    serverId = urlParams.get("serverId");
    channelId = urlParams.get("channelId");

    if (!serverId || !channelId) {
        alert("Missing serverId or channelId in URL");
        return;
    }

    const token = localStorage.getItem("token");
    if (!token) {
        alert("You must be logged in. JWT token not found.");
        return;
    }

    const socket = new SockJS(`/signal-ws?token=${token}`);
    stompClient = Stomp.over(socket);

    stompClient.connect({}, () => {
        console.log('✅ Connected to STOMP signaling server');

        stompClient.subscribe(`/topic/video/${serverId}/${channelId}`, (message) => {
            const signal = JSON.parse(message.body);
            console.log('📩 Signal received:', signal);
            handleSignal(signal);
        });

        initWebRTC();
    }, (error) => {
        console.error("❌ STOMP connection error:", error);
        alert("Failed to connect to signaling server. Please make sure you're authenticated.");
    });
}

function sendSignal(signal) {
    signal.serverId = serverId;
    signal.channelId = channelId;
    stompClient.send("/app/signal", {}, JSON.stringify(signal));
}

async function initWebRTC() {
    peerConnection = new RTCPeerConnection({
        iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
    });

    peerConnection.onicecandidate = e => {
        if (e.candidate) {
            console.log('🔼 Sending ICE candidate');
            sendSignal({ type: 'candidate', candidate: e.candidate });
        }
    };

    peerConnection.ontrack = e => {
        const remoteVideo = document.getElementById('remoteVideo');
        if (remoteVideo.srcObject !== e.streams[0]) {
            remoteVideo.srcObject = e.streams[0];
            console.log('🎥 Remote stream attached');
        }
    };

    try {
        const localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        document.getElementById('localVideo').srcObject = localStream;

        localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));
        console.log('🎙️ Local stream captured');

        const offer = await peerConnection.createOffer();
        await peerConnection.setLocalDescription(offer);
        console.log('📤 Sending offer');
        sendSignal(offer);
    } catch (err) {
        console.error('❌ Error accessing camera/mic:', err);
        alert('Please allow camera/microphone access or check device permissions.');
    }
}

function handleSignal(signal) {
    switch (signal.type) {
        case 'offer':
            handleOffer(signal);
            break;
        case 'answer':
            handleAnswer(signal);
            break;
        case 'candidate':
            handleCandidate(signal);
            break;
    }
}

function handleOffer(offer) {
    console.log('📥 Offer received');
    peerConnection.setRemoteDescription(new RTCSessionDescription(offer))
        .then(() => peerConnection.createAnswer())
        .then(answer => {
            peerConnection.setLocalDescription(answer);
            console.log('📤 Sending answer');
            sendSignal(answer);
        })
        .catch(err => console.error('Error handling offer:', err));
}

function handleAnswer(answer) {
    console.log('📥 Answer received');
    peerConnection.setRemoteDescription(new RTCSessionDescription(answer))
        .catch(err => console.error('Error handling answer:', err));
}

function handleCandidate(signal) {
    console.log('📥 ICE candidate received');
    peerConnection.addIceCandidate(new RTCIceCandidate(signal.candidate))
        .catch(err => console.error('ICE error:', err));
}

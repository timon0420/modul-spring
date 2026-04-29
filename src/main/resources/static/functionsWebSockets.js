var websocket;
var dataPointsParam = [];

for (var i = 0; i < 30; i++) {
    dataPointsParam[i] = { label: i, y: 0 };
}

function getRootUri() {
    var protocol = (location.protocol === "https:" ? "wss://" : "ws://");
    var host = (location.hostname === "" ? "localhost" : location.hostname);
    var port = (location.port === "" ? "8080" : location.port);
    
    return protocol + host + ":" + port;
}

function getContextPath() {
    return window.location.pathname.substring(0, window.location.pathname.indexOf("/", 2));
}
var wsUri = getRootUri() + getContextPath() + "/webSocketEndpointJSON";

function init() {
    drawChart(dataPointsParam);
    initWebSocket();
}

function initWebSocket() {
    console.log("Łączenie z: " + wsUri);
    websocket = new WebSocket(wsUri);
    
    websocket.onopen = function (evt) {
        onOpen(evt);
    };
    websocket.onmessage = function (evt) {
        onMessage(evt);
    };
    websocket.onerror = function (evt) {
        onError(evt);
    };
}

function onOpen(evt) {
    writeToScreen("CONNECTED");
    setInterval(function() {
        doSend("x");
    }, 1000);
}

function onMessage(evt) {
    writeToScreen("Received: " + evt.data);
    try {
        var dataArrayJSON = JSON.parse(evt.data);
        for (var i = 0; i < dataArrayJSON.length; i++) {
            dataPointsParam[i] = { label: i, y: dataArrayJSON[i] };
        }
        drawChart(dataPointsParam);
    } catch (e) {
        console.log("Błąd parsowania JSON: " + e);
    }
}

function onError(evt) {
    writeToScreen("<span style='color: red;'>ERROR: </span>" + (evt.data || "Połączenie przerwane"));
    console.error("WebSocket Error:", evt);
}

function doSend(message) {
    if (websocket && websocket.readyState === WebSocket.OPEN) {
        websocket.send(message);
    }
}

function writeToScreen(message) {
    var messageField = document.getElementById("messageStatusID");
    if (messageField) {
        messageField.value = message;
    }
}

function drawChart(dataPoints) {
    var chart = new CanvasJS.Chart("chartContainer", {
        title: {
            text: "Wykres CanvasJS"
        },
        axisY: {
            title: "Wartość",
            maximum: 100 
        },
        data: [{ 
            type: "column",
            dataPoints: dataPoints
        }]
    });
    chart.render();
}

window.addEventListener("load", init, false);
var express = require('express');
var firebase = require('firebase');
var bodyParser = require('body-parser');
var fs = require('fs');


// Writes asynchronously

// Creates the app for express
var app = express();

// Enables json parsing
app.use(bodyParser.json());

// Initializing Firebase Backend
var myDataRef = new Firebase('https://luminous-heat-8591.firebaseio.com/');

// Server responding to GET request
app.get('/', function (req,res) {
	myDataRef.once("value", function(snap) {
		console.log("All data: ", snap.val());
	});
	res.sendFile(__dirname+ '/index.html');
});


// Server responding to POST request
// TODO: MODIFY URL??
app.post('/', function (req,res) {

	// Storing data into database


    var body = '';
    // Event listener for starting to read data (save data here)
    // Right now we only use these event listeners to debug
    req.on('data', function (data) {
        body += data;
        console.log("Partial body: " + body);
        

        // Parsing the data from the request (TODO: NEED TO USE REAL JSON ATTRIBUTES)
        
        // TODO: UNCOMMENT THIS AFTER WE GET ACTUAL IMPLEMENTATION WORKING!
        myDataRef.push({requestText:body});

        // var x = req.body.x;
        // var y = req.body.y;
        // var z = req.body.z;
        // var image = req.body.image;

        // TODO: UNCOMMENT: myDataRef.push({image:image, x:x, y:y, z:z});

    });

    // Event listener for after end of data reached (perform actions to be done after data is processed here)
    req.on('end', function () {
        console.log("Body: " + body);

    });

    // Writing to the response and sending it
    res.writeHead(200, {'Content-Type': 'text/plain'});
    res.end('post received');
});

// App listening on Port 3000
app.listen(3000, function() {
  console.log("Listening on Port 3000");
});
var express = require('express');
var firebase = require('firebase');
var bodyParser = require('body-parser');

// Creates the app for express
var app = express();

// Enables json parsing
app.use(bodyParser.json());

// Initializing Firebase Backend
var myDataRef = new Firebase('https://luminous-heat-8591.firebaseio.com/');

// Server responding to GET request
// TODO: MODIFY URL??
app.get('/', function (req,res) {
	// Fetch data
	var xValTarget = req.body.xVal;
	var yValTarget = req.body.yVal;
	var zValTarget = req.body.zVal;


	// Performs this function once
	myDataRef.once("value", function(snapshot) {
		// TODO: CREATE INBOUNDS FUNCTIOn
		snapshot.forEach(function(data) {
			if (inBounds(xValTarget, yValTarget, zValTarget, data)) {
				// TODO: PERFORM DESIRED ACTION HERE
			};
		});
	});

});


// Server responding to POST request
// TODO: MODIFY URL??
app.post('/', function (req,res) {

	// Parsing the data from the request (TODO: NEED TO USE REAL JSON ATTRIBUTES)
	// TODO: NEED TO FIND A WAY TO ENCODE IMAGE INTO BASE-64 INT
	var noteText = req.body.noteText;
	var xVal = req.body.xVal;
	var yVal = req.body.yVal;
	var zVal = req.body.zVal;

	// Storing data into database
	myDataRef.push({noteText:noteText, xVal:xVal, yVal:yVal, zVal:zVal});


    var body = '';
    // Event listener for starting to read data (save data here)
    // Right now we only use these event listeners to debug
    req.on('data', function (data) {
        body += data;
        console.log("Partial body: " + body);
    });

    // Event listener for after end of data reached (process data here)
    req.on('end', function () {
        console.log("Body: " + body);
        writeAsync(body);
    });

    // Writing to the response and sending it
    res.writeHead(200, {'Content-Type': 'text/plain'});
    res.end('post received');
});

// App listening on Port 3000
app.listen(3000, function() {
  console.log("Listening on Port 3000");
});

function createAudioPlayer( audioSource ) {
	if( typeof( webkitAudioContext ) === "function" ) {
		return new WebkitAudioPlayer( audioSource );
	} else {
		return new FirefoxAudioPlayer( audioSource );
	}
}

function WebkitAudioPlayer( audioSource ) {
	var audioContext = new webkitAudioContext();
	var scriptProcessor = audioContext.createJavaScriptNode( 4096, 0, 2 );
	var buffer = new Float32Array( scriptProcessor.bufferSize * 2 );
	audioSource.setSamplingRate( audioContext.sampleRate );
	scriptProcessor.onaudioprocess = function( event ) {
		var lOut = event.outputBuffer.getChannelData( 0 );
		var rOut = event.outputBuffer.getChannelData( 1 );
		audioSource.getAudio( buffer, event.outputBuffer.length );
		for( var bufIdx = 0, outIdx = 0; bufIdx < buffer.length; bufIdx += 2, outIdx++ ) {
			lOut[ outIdx ] = buffer[ bufIdx ];
			rOut[ outIdx ] = buffer[ bufIdx + 1 ];
		}
	}
	this.play = function() {
		scriptProcessor.connect( audioContext.destination );
	}
	this.stop = function() {
		scriptProcessor.disconnect( audioContext.destination );
	}
}

function FirefoxAudioPlayer( audioSource ) {
	// 44100hz seems to sound best in Firefox.
	audioSource.setSamplingRate( 44100 );
	var samplingRate = audioSource.getSamplingRate();
	var buffer = new Float32Array( samplingRate / 2 /* 250ms */ );
	var bufferIndex = buffer.length, intervalId = null;
	var audio = new Audio();
	audio.mozSetup( 2, samplingRate );
	this.play = function() {
		if( intervalId == null ) {
			var oldTime = new Date().getTime();
			intervalId = setInterval( function( a ) {
				var newTime = new Date().getTime();
				if( newTime - oldTime < 250 ) {
					// Only write audio if the event came in roughly on time.
					// Prevents stuttering when the script is running in the background.
					if( bufferIndex < buffer.length ) {
						// Finish writing current buffer.
						bufferIndex += audio.mozWriteAudio( buffer.subarray( bufferIndex ) );
					}
					while( bufferIndex >= buffer.length ) {
						// Write as many full buffers as possible.
						audioSource.getAudio( buffer, buffer.length >> 1 );
						bufferIndex = audio.mozWriteAudio( buffer );
					}
				}
				oldTime = newTime;
			}, 125 );
		}
	}
	this.stop = function() {
		if( intervalId != null ) {
			clearInterval( intervalId );
			intervalId = null;
		}
	}
}

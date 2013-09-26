
function createAudioPlayer() {
	if( typeof( AudioContext ) === "function" ) {
		return new AudioPlayer( new AudioContext() );
	} else if( typeof( webkitAudioContext ) === "function" ) {
		return new AudioPlayer( new webkitAudioContext() );
	} else {
		return new FirefoxAudioPlayer();
	}
}

function AudioPlayer( audioContext ) {
	var scriptProcessor = audioContext.createJavaScriptNode( 4096, 0, 2 );
	var buffer = new Float32Array( scriptProcessor.bufferSize * 2 );
	var audioSource = new SineSource( audioContext.sampleRate );
	scriptProcessor.onaudioprocess = function( event ) {
		var lOut = event.outputBuffer.getChannelData( 0 );
		var rOut = event.outputBuffer.getChannelData( 1 );
		audioSource.getAudio( buffer, event.outputBuffer.length );
		for( var bufIdx = 0, outIdx = 0; bufIdx < buffer.length; bufIdx += 2, outIdx++ ) {
			lOut[ outIdx ] = buffer[ bufIdx ];
			rOut[ outIdx ] = buffer[ bufIdx + 1 ];
		}
	}
	this.getSamplingRate = function() {
		return audioContext.sampleRate;
	}
	this.setAudioSource = function( audioSrc ) {
		audioSource = audioSrc;
	}
	this.play = function() {
		scriptProcessor.connect( audioContext.destination );
	}
	this.stop = function() {
		scriptProcessor.disconnect( audioContext.destination );
	}
}

function FirefoxAudioPlayer() {
	// 44100hz seems to sound best in Firefox.
	var samplingRate = 44100;
	var buffer = new Float32Array( samplingRate >> 1 /* 250ms */ );
	var bufferIndex = buffer.length, intervalId = null;
	var audioSource = new SineSource( samplingRate );
	var audio = new Audio();
	audio.mozSetup( 2, samplingRate );
	this.getSamplingRate = function() {
		return samplingRate;
	}
	this.setAudioSource = function( audioSrc ) {
		audioSource = audioSrc;
	}
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

function SineSource( samplingRate ) {
	// Simple AudioSource for testing.
	this.getSamplingRate = function() {
		return rate;
	}
	this.getAudio = function( buffer, count ) {
		for( idx = 0, end = count * 2; idx < end; idx += 2, phase++ ) {
			var x = phase * freq;
			buffer[ idx ] = Math.sin( x );
			buffer[ idx + 1 ] = Math.sin( x * 0.5 );
		}
	}
	var rate = samplingRate;
	var freq = 2 * Math.PI * 440 / rate;
	var phase = 0;
}


function AudioPlayer( audioSource, latency ) {
	var samplingRate = audioSource.getSamplingRate();
	var buffer = new Float32Array( samplingRate >> 1 /* 250ms */ );
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

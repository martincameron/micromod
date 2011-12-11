
function AudioPlayer( audioSource ) {
	var buffer = new Float32Array( audioSource.getMaxBufferSize() );
	var audio = new Audio();
	audio.mozSetup( 2, audioSource.getSamplingRate() );
	var bufferIndex = 0;
	var bufferCount = 0;
	var playing = false;
	var interval;

	this.play = function() {
		if( !playing ) {
			interval = setInterval( this.writeAudio, 100 );
			playing = true;
		}
	}

	this.stop = function() {
		if( playing ) {
			clearInterval( interval );
			playing = false;
		}
	}

	this.writeAudio = function() {
		if( bufferIndex < bufferCount ) {
			bufferIndex += audio.mozWriteAudio( buffer.subarray( bufferIndex, bufferCount ) );
		}
		while( bufferIndex >= bufferCount ) {
			// Write as many whole chunks as possible.
			bufferCount = audioSource.getAudio( buffer );
			bufferIndex = audio.mozWriteAudio( buffer.subarray( 0, bufferCount ) );
		}
	}
}


function AudioPlayer( audioSource, bufferTime ) {
	if( !bufferTime ) {
		bufferTime = 300;
	}
	var rate = audioSource.getSamplingRate();
	var audio = new Audio();
	audio.mozSetup( 2, rate );
	var buffer = new Float32Array( rate * 2 * bufferTime / 1000 );
	var bufferIndex = 0;
	var playing = false;
	var interval, date;

	this.play = function() {
		if( !playing ) {
			date = new Date();
			playing = true;
			interval = setInterval( this.writeAudio, bufferTime / 2 );
		}
	}

	this.stop = function() {
		if( playing ) {
			clearInterval( interval );
			playing = false;
		}
	}

	this.writeAudio = function() {
		var time = date.getTime();
		date = new Date();
		if( ( date.getTime() - time ) < bufferTime ) {
			// Only write audio if the interval is sufficient to prevent stuttering.
			if( bufferIndex < buffer.length ) {
				// Finish writing current buffer.
				bufferIndex += audio.mozWriteAudio( buffer.subarray( bufferIndex ) );
			}
			while( bufferIndex >= buffer.length ) {
				// Refill buffer and write as many as possible.
				audioSource.getAudio( buffer, buffer.length >> 1 );
				bufferIndex = audio.mozWriteAudio( buffer );
			}
		}
	}
}

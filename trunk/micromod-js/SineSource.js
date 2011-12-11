
function SineSource( sampleRate ) {
	this.getSamplingRate = function() {
		return sampleRate;
	}

	this.getMaxBufferSize = function() {
		return 1024;
	}

	this.getAudio = function( buffer ) {
		for( idx = 0; idx < 1024; idx += 2 ) {
			buffer[ idx ] = Math.sin( Math.PI * idx / 256 );
			buffer[ idx + 1 ] = Math.sin( Math.PI * idx / 512 );
		}
		return 1024;
	}
}

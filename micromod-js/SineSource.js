
function SineSource( sampleRate ) {
	var freq = 2 * Math.PI * 440 / sampleRate;
	var phase = 0;

	this.getSamplingRate = function() {
		return sampleRate;
	}

	this.getAudio = function( buffer, count ) {
		for( idx = 0, end = count * 2; idx < end; idx += 2, phase++ ) {
			var x = phase * freq;
			buffer[ idx ] = Math.sin( x );
			buffer[ idx + 1 ] = Math.sin( x * 0.5 );
		}
	}
}

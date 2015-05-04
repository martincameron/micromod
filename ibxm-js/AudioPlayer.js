
function AudioPlayer() {
	var audioContext = new AudioContext();
	var scriptProcessor = audioContext.createScriptProcessor( 0, 0, 2 );
	var audioSource = new SineSource( audioContext.sampleRate );
	var onaudioprocess = function( event ) {
		var leftBuf = event.outputBuffer.getChannelData( 0 );
		var rightBuf = event.outputBuffer.getChannelData( 1 );
		audioSource.getAudio( leftBuf, rightBuf, event.outputBuffer.length );
	}
	this.getSamplingRate = function() {
		return audioContext.sampleRate;
	}
	this.setAudioSource = function( audioSrc ) {
		audioSource = audioSrc;
	}
	this.play = function() {
		scriptProcessor.onaudioprocess = onaudioprocess;
		scriptProcessor.connect( audioContext.destination );
	}
	this.stop = function() {
		scriptProcessor.disconnect( audioContext.destination );
		scriptProcessor.onaudioprocess = null;
	}
}

function SineSource( samplingRate ) {
	// Simple AudioSource for testing.
	var rate = samplingRate;
	var freq = 2 * Math.PI * 440 / rate;
	var phase = 0;
	this.getSamplingRate = function() {
		return rate;
	}
	this.getAudio = function( leftBuffer, rightBuffer, count ) {
		for( var idx = 0; idx < count; idx++, phase++ ) {
			leftBuffer[ idx ] = Math.sin( phase * freq );
			rightBuffer[ idx ] = Math.sin( phase * freq * 0.5 );
		}
	}
}

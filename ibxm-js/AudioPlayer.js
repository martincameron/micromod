
function AudioPlayer() {
	var audioContext = new AudioContext();
	var scriptProcessor = audioContext.createScriptProcessor( 0, 0, 2 );
	var buffer = new Float32Array( scriptProcessor.bufferSize * 2 );
	var audioSource = new SineSource( audioContext.sampleRate );
	var onaudioprocess = function( event ) {
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

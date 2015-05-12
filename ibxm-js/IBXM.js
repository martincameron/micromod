
function IBXMReplay( module, samplingRate ) {
	/* Return a String representing the version of the replay. */
	this.getVersion = function() {
		return "20150512 (c)2015 mumart@gmail.com";
	}
	/* Return the sampling rate of playback. */
	this.getSamplingRate = function() {
		return samplingRate;
	}
	/* Set the sampling rate of playback.
	   This can be used with Module.c2Rate to adjust the tempo and pitch.*/
	this.setSamplingRate = function( rate ) {
		if( rate < 8000 || rate > 128000 ) {
			throw "Unsupported sampling rate!";
		}
		samplingRate = rate;
	}
	/* Enable or disable the linear interpolation filter. */
	this.setInterpolation = function( interp ) {
		interpolation = interp;
	}
	/* Get the current row position. */
	this.getRow = function() {
		return row;
	}
	/* Get the current pattern position in the sequence. */
	this.getSequencePos = function() {
		return seqPos;
	}
	/* Set the pattern in the sequence to play.
	   The tempo is reset to the default. */
	this.setSequencePos = function( pos ) {
		if( pos >= module.sequenceLength ) {
			pos = 0;
		}
		breakSeqPos = pos;
		nextRow = 0;
		tick = 1;
		this.globalVol = module.defaultGVol;
		speed = module.defaultSpeed > 0 ? module.defaultSpeed : 6;
		tempo = module.defaultTempo > 0 ? module.defaultTempo : 125;
		plCount = plChannel = -1;
		for( var idx = 0; idx < module.numChannels; idx++ ) {
			channels[ idx ] = new IBXMChannel( this, idx );
		}
		for( var idx = 0; idx < 128; idx++ ) {
			rampBuf[ idx ] = 0;
		}
		mixIdx = mixLen = 0;
		seqTick();
	}
	/* Returns the song duration in samples at the current sampling rate. */
	this.calculateSongDuration = function() {
		var duration = 0;
		this.setSequencePos( 0 );
		var songEnd = false;
		while( !songEnd ) {
			duration += calculateTickLen( tempo, samplingRate );
			songEnd = seqTick();
		}
		this.setSequencePos( 0 );
		return duration;
	}
	/* Seek to approximately the specified sample position.
	   The actual sample position reached is returned. */
	this.seek = function( samplePos ) {
		this.setSequencePos( 0 );
		var currentPos = 0;
		var tickLen = calculateTickLen( tempo, samplingRate );
		while( ( samplePos - currentPos ) >= tickLen ) {
			for( var idx = 0; idx < module.numChannels; idx++ ) {
				channels[ idx ].updateSampleIdx( tickLen * 2, samplingRate * 2 );
			}
			currentPos += tickLen;
			seqTick();
			tickLen = calculateTickLen( tempo, samplingRate );
		}
		return currentPos;
	}
	/* Seek to the specified position and row in the sequence. */
	this.seekSequencePos = function( sequencePos, sequenceRow ) {
		this.setSequencePos( 0 );
		if( sequencePos < 0 || sequencePos >= module.sequenceLength ) {
			sequencePos = 0;
		}
		if( sequenceRow >= module.patterns[ module.sequence[ sequencePos ] ].numRows ) {
			sequenceRow = 0;
		}
		while( seqPos < sequencePos || row < sequenceRow ) {
			var tickLen = calculateTickLen( tempo, samplingRate );
			for( var idx = 0; idx < module.numChannels; idx++ ) {
				channels[ idx ].updateSampleIdx( tickLen * 2, samplingRate * 2 );
			}
			if( seqTick() ) { /* Song end reached.*/
				setSequencePos( sequencePos );
				return;
			}
		}
	}
	/* Write count floating-point stereo samples into the specified buffers. */
	this.getAudio = function( leftBuf, rightBuf, count ) {
		var outIdx = 0;
		while( outIdx < count ) {
			if( mixIdx >= mixLen ) {
				mixLen = mixAudio();
				mixIdx = 0;
			}
			var remain = mixLen - mixIdx;
			if( ( outIdx + remain ) > count ) {
				remain = count - outIdx;
			}
			for( var end = outIdx + remain; outIdx < end; outIdx++, mixIdx++ ) {
				leftBuf[ outIdx ] = mixBuf[ mixIdx * 2 ];
				rightBuf[ outIdx ] = mixBuf[ mixIdx * 2 + 1 ];
			}
		}
	}
	var mixAudio = function() {
		/* Generate audio. The number of samples produced is returned.*/
		var tickLen = calculateTickLen( tempo, samplingRate );
		for( var idx = 0, end = ( tickLen + 65 ) * 4; idx < end; idx++ ) {
			/* Clear mix buffer.*/
			mixBuf[ idx ] = 0;
		}
		for( var idx = 0; idx < module.numChannels; idx++ ) {
			/* Resample and mix each channel.*/
			var chan = channels[ idx ];
			chan.resample( mixBuf, 0, ( tickLen + 65 ) * 2, samplingRate * 2, interpolation );
			chan.updateSampleIdx( tickLen * 2, samplingRate * 2 );
		}
		downsample( mixBuf, tickLen + 64 );
		volumeRamp( tickLen );
		/* Update the sequencer.*/
		seqTick();
		return tickLen;
	}
	var calculateTickLen = function( tempo, sampleRate ) {
		return ( ( sampleRate * 5 ) / ( tempo * 2 ) ) | 0;
	}
	var volumeRamp = function( tickLen ) {
		var rampRate = 2048 / samplingRate;
		for( var idx = 0, a1 = 0; a1 < 1; idx += 2, a1 += rampRate ) {
			var a2 = 1 - a1;
			mixBuf[ idx     ] = mixBuf[ idx     ] * a1 + rampBuf[ idx     ] * a2;
			mixBuf[ idx + 1 ] = mixBuf[ idx + 1 ] * a1 + rampBuf[ idx + 1 ] * a2;
		}
		rampBuf.set( mixBuf.subarray( tickLen * 2, ( tickLen + 64 ) * 2 ) );
	}
	var downsample = function( buf, count ) {
		/* 2:1 downsampling with simple but effective anti-aliasing.*/
		/* Buf must contain count * 2 + 1 stereo samples.*/
		var outLen = count * 2;
		for( inIdx = 0, outIdx = 0; outIdx < outLen; inIdx += 4, outIdx += 2 ) {
			buf[ outIdx     ] = buf[ inIdx     ] * 0.25 + buf[ inIdx + 2 ] * 0.5 + buf[ inIdx + 4 ] * 0.25;
			buf[ outIdx + 1 ] = buf[ inIdx + 1 ] * 0.25 + buf[ inIdx + 3 ] * 0.5 + buf[ inIdx + 5 ] * 0.25;
		}
	}
	var seqTick = function() {
		var songEnd = false;
		if( --tick <= 0 ) {
			tick = speed;
			songEnd = seqRow();
		} else {
			for( var idx = 0; idx < module.numChannels; idx++ ) {
				channels[ idx ].tick();
			}
		}
		return songEnd;
	}
	var seqRow = function() {
		var songEnd = false;
		if( breakSeqPos >= 0 ) {
			if( breakSeqPos >= module.sequenceLength ) {
				breakSeqPos = nextRow = 0;
			}
			if( breakSeqPos <= seqPos ) {
				songEnd = true;
			}
			seqPos = breakSeqPos;
			for( var idx = 0; idx < module.numChannels; idx++ ) {
				channels[ idx ].plRow = 0;
			}
			breakSeqPos = -1;
		}
		var pattern = module.patterns[ module.sequence[ seqPos ] ];
		row = nextRow;
		if( row >= pattern.numRows ) row = 0;
		nextRow = row + 1;
		if( nextRow >= pattern.numRows ) {
			breakSeqPos = seqPos + 1;
			nextRow = 0;
		}
		var noteIdx = row * module.numChannels;
		for( var chanIdx = 0; chanIdx < module.numChannels; chanIdx++ ) {
			var channel = channels[ chanIdx ];
			pattern.getNote( noteIdx + chanIdx, note );
			if( note.effect == 0xE ) {
				note.effect = 0x70 | ( note.param >> 4 );
				note.param &= 0xF;
			}
			if( note.effect == 0x93 ) {
				note.effect = 0xF0 | ( note.param >> 4 );
				note.param &= 0xF;
			}
			if( note.effect == 0 && note.param > 0 ) note.effect = 0x8A;
			channel.row( note );
			switch( note.effect ) {
				case 0x81: /* Set Speed. */
					if( note.param > 0 )
						tick = speed = note.param;
					break;
				case 0xB: case 0x82: /* Pattern Jump.*/
					if( plCount < 0 ) {
						breakSeqPos = note.param;
						nextRow = 0;
					}
					break;
				case 0xD: case 0x83: /* Pattern Break.*/
					if( plCount < 0 ) {
						breakSeqPos = seqPos + 1;
						nextRow = ( note.param >> 4 ) * 10 + ( note.param & 0xF );
					}
					break;
				case 0xF: /* Set Speed/Tempo.*/
					if( note.param > 0 ) {
						if( note.param < 32 )
							tick = speed = note.param;
						else
							tempo = note.param;
					}
					break;
				case 0x94: /* Set Tempo.*/
					if( note.param > 32 )
						tempo = note.param;
					break;
				case 0x76: case 0xFB : /* Pattern Loop.*/
					if( note.param == 0 ) /* Set loop marker on this channel. */
						channel.plRow = row;
					if( channel.plRow < row ) { /* Marker valid. Begin looping. */
						if( plCount < 0 ) { /* Not already looping, begin. */
							plCount = note.param;
							plChannel = chanIdx;
						}
						if( plChannel == chanIdx ) { /* Next Loop.*/
							if( plCount == 0 ) { /* Loop finished. */
								/* Invalidate current marker. */
								channel.plRow = row + 1;
							} else { /* Loop and cancel any breaks on this row. */
								nextRow = channel.plRow;
								breakSeqPos = -1;
							}
							plCount--;
						}
					}
					break;
				case 0x7E: case 0xFE: /* Pattern Delay.*/
					tick = speed + speed * note.param;
					break;
			}
		}
		return songEnd;
	}
	var interpolation = true;
	var rampBuf = new Float32Array( 64 * 2 );
	var mixBuf = new Float32Array( ( calculateTickLen( 32, 128000 ) + 65 ) * 4 );
	var mixIdx = 0, mixLen = 0;
	var seqPos = 0, breakSeqPos = 0, row = 0, nextRow = 0, tick = 0;
	var speed = 0, tempo = 0, plCount = 0, plChannel = 0;
	var channels = new Array( module.numChannels );
	var note = new IBXMNote();
	this.module = module;
	this.globalVol = 0;
	this.setSamplingRate( samplingRate );
	this.setSequencePos( 0 );
}

function IBXMChannel( replay, id ) {
	var sineTable = new Int16Array([
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	]);
	var instrument = new IBXMInstrument();
	var sample = instrument.samples[ 0 ];
	var keyOn = false;
	var noteKey = 0, noteIns = 0, noteVol = 0, noteEffect = 0, noteParam = 0;
	var sampleOffset = 0, sampleIdx = 0, freq = 0, ampl = 0, pann = 0;
	var volume = 0, panning = replay.module.defaultPanning[ id ];
	var fadeOutVol = 0, volEnvTick = 0, panEnvTick = 0;
	var period = 0, portaPeriod = 0, retrigCount = 0, fxCount = 0, autoVibratoCount = 0;
	var portaUpParam = 0, portaDownParam = 0, tonePortaParam = 0, offsetParam = 0;
	var finePortaUpParam = 0, finePortaDownParam = 0, extraFinePortaParam = 0;
	var arpeggioParam = 0, vslideParam = 0, globalVslideParam = 0, panningSlideParam = 0;
	var fineVslideUpParam = 0, fineVslideDownParam = 0;
	var retrigVolume = 0, retrigTicks = 0, tremorOnTicks = 0, tremorOffTicks = 0;
	var vibratoType = 0, vibratoPhase = 0, vibratoSpeed = 0, vibratoDepth = 0;
	var tremoloType = 0, tremoloPhase = 0, tremoloSpeed = 0, tremoloDepth = 0;
	var tremoloAdd = 0, vibratoAdd = 0, arpeggioAdd = 0;
	var randomSeed = ( id + 1 ) * 0xABCDEF;
	this.plRow = 0;
	this.resample = function( outBuf, offset, count, sampleRate, interpolate ) {
		if( ampl <= 0 ) return;
		var lGain = ampl * ( 1 - pann ) / 32768;
		var rGain = ampl * pann / 32768;
		var samIdx = sampleIdx;
		var step = freq / sampleRate;
		var loopLen = sample.loopLength;
		var loopEnd = sample.loopStart + loopLen;
		var sampleData = sample.sampleData;
		var outIdx = offset * 2;
		var outEnd = ( offset + count ) * 2;
		if( interpolate ) {
			while( outIdx < outEnd ) {
				if( samIdx >= loopEnd ) {
					if( loopLen <= 1 ) break;
					while( samIdx >= loopEnd ) samIdx -= loopLen;
				}
				var x = samIdx | 0;
				var c = sampleData[ x ];
				var m = sampleData[ x + 1 ] - c;
				var y = ( m * ( samIdx - x ) ) + c;
				outBuf[ outIdx++ ] += y * lGain;
				outBuf[ outIdx++ ] += y * rGain;
				samIdx += step;
			}
		} else {
			while( outIdx < outEnd ) {
				if( samIdx >= loopEnd ) {
					if( loopLen <= 1 ) break;
					while( samIdx >= loopEnd ) samIdx -= loopLen;
				}
				var y = sampleData[ samIdx | 0 ];
				outBuf[ outIdx++ ] += y * lGain;
				outBuf[ outIdx++ ] += y * rGain;
				samIdx += step;
			}
		}
	}
	this.updateSampleIdx = function( count, sampleRate ) {
		sampleIdx += ( freq / sampleRate ) * count;
		if( sampleIdx > sample.loopStart ) {
			if( sample.loopLength > 1 ) {
				sampleIdx = sample.loopStart + ( sampleIdx - sample.loopStart ) % sample.loopLength;
			} else {
				sampleIdx = sample.loopStart;
			}
		}
	}
	this.row = function( note ) {
		noteKey = note.key;
		noteIns = note.instrument;
		noteVol = note.volume;
		noteEffect = note.effect;
		noteParam = note.param;
		retrigCount++;
		vibratoAdd = tremoloAdd = arpeggioAdd = fxCount = 0;
		if( !( ( noteEffect == 0x7D || noteEffect == 0xFD ) && noteParam > 0 ) ) {
			/* Not note delay.*/
			trigger();
		}
		switch( noteEffect ) {
			case 0x01: case 0x86: /* Porta Up. */
				if( noteParam > 0 ) portaUpParam = noteParam;
				portamentoUp( portaUpParam );
				break;
			case 0x02: case 0x85: /* Porta Down. */
				if( noteParam > 0 ) portaDownParam = noteParam;
				portamentoDown( portaDownParam );
				break;
			case 0x03: case 0x87: /* Tone Porta. */
				if( noteParam > 0 ) tonePortaParam = noteParam;
				break;
			case 0x04: case 0x88: /* Vibrato. */
				if( ( noteParam >> 4 ) > 0 ) vibratoSpeed = noteParam >> 4;
				if( ( noteParam & 0xF ) > 0 ) vibratoDepth = noteParam & 0xF;
				vibrato( false );
				break;
			case 0x05: case 0x8C: /* Tone Porta + Vol Slide. */
				if( noteParam > 0 ) vslideParam = noteParam;
				volumeSlide();
				break;
			case 0x06: case 0x8B: /* Vibrato + Vol Slide. */
				if( noteParam > 0 ) vslideParam = noteParam;
				vibrato( false );
				volumeSlide();
				break;
			case 0x07: case 0x92: /* Tremolo. */
				if( ( noteParam >> 4 ) > 0 ) tremoloSpeed = noteParam >> 4;
				if( ( noteParam & 0xF ) > 0 ) tremoloDepth = noteParam & 0xF;
				tremolo();
				break;
			case 0x08: /* Set Panning.*/
				panning = ( noteParam < 128 ) ? ( noteParam << 1 ) : 255;
				break;
			case 0x0A: case 0x84: /* Vol Slide. */
				if( noteParam > 0 ) vslideParam = noteParam;
				volumeSlide();
				break;
			case 0x0C: /* Set Volume. */
				volume = noteParam >= 64 ? 64 : noteParam & 0x3F;
				break;
			case 0x10: case 0x96: /* Set Global Volume. */
				replay.globalVol = noteParam >= 64 ? 64 : noteParam & 0x3F;
				break;
			case 0x11: /* Global Volume Slide. */
				if( noteParam > 0 ) globalVslideParam = noteParam;
				break;
			case 0x14: /* Key Off. */
				keyOn = false;
				break;
			case 0x15: /* Set Envelope Tick. */
				volEnvTick = panEnvTick = noteParam & 0xFF;
				break;
			case 0x19: /* Panning Slide. */
				if( noteParam > 0 ) panningSlideParam = noteParam;
				break;
			case 0x1B: case 0x91: /* Retrig + Vol Slide. */
				if( ( noteParam >> 4 ) > 0 ) retrigVolume = noteParam >> 4;
				if( ( noteParam & 0xF ) > 0 ) retrigTicks = noteParam & 0xF;
				retrigVolSlide();
				break;
			case 0x1D: case 0x89: /* Tremor. */
				if( ( noteParam >> 4 ) > 0 ) tremorOnTicks = noteParam >> 4;
				if( ( noteParam & 0xF ) > 0 ) tremorOffTicks = noteParam & 0xF;
				tremor();
				break;
			case 0x21: /* Extra Fine Porta. */
				if( noteParam > 0 ) extraFinePortaParam = noteParam;
				switch( extraFinePortaParam & 0xF0 ) {
					case 0x10:
						portamentoUp( 0xE0 | ( extraFinePortaParam & 0xF ) );
						break;
					case 0x20:
						portamentoDown( 0xE0 | ( extraFinePortaParam & 0xF ) );
						break;
				}
				break;
			case 0x71: /* Fine Porta Up. */
				if( noteParam > 0 ) finePortaUpParam = noteParam;
				portamentoUp( 0xF0 | ( finePortaUpParam & 0xF ) );
				break;
			case 0x72: /* Fine Porta Down. */
				if( noteParam > 0 ) finePortaDownParam = noteParam;
				portamentoDown( 0xF0 | ( finePortaDownParam & 0xF ) );
				break;
			case 0x74: case 0xF3: /* Set Vibrato Waveform. */
				if( noteParam < 8 ) vibratoType = noteParam;
				break;
			case 0x77: case 0xF4: /* Set Tremolo Waveform. */
				if( noteParam < 8 ) tremoloType = noteParam;
				break;
			case 0x7A: /* Fine Vol Slide Up. */
				if( noteParam > 0 ) fineVslideUpParam = noteParam;
				volume += fineVslideUpParam;
				if( volume > 64 ) volume = 64;
				break;
			case 0x7B: /* Fine Vol Slide Down. */
				if( noteParam > 0 ) fineVslideDownParam = noteParam;
				volume -= fineVslideDownParam;
				if( volume < 0 ) volume = 0;
				break;
			case 0x7C: case 0xFC: /* Note Cut. */
				if( noteParam <= 0 ) volume = 0;
				break;
			case 0x8A: /* Arpeggio. */
				if( noteParam > 0 ) arpeggioParam = noteParam;
				break;
			case 0x95: /* Fine Vibrato.*/
				if( ( noteParam >> 4 ) > 0 ) vibratoSpeed = noteParam >> 4;
				if( ( noteParam & 0xF ) > 0 ) vibratoDepth = noteParam & 0xF;
				vibrato( true );
				break;
			case 0xF8: /* Set Panning. */
				panning = noteParam * 17;
				break;
		}
		autoVibrato();
		calculateFrequency();
		calculateAmplitude();
		updateEnvelopes();
	}
	this.tick = function() {
		vibratoAdd = 0;
		fxCount++;
		retrigCount++;
		if( !( noteEffect == 0x7D && fxCount <= noteParam ) ) {
			switch( noteVol & 0xF0 ) {
				case 0x60: /* Vol Slide Down.*/
					volume -= noteVol & 0xF;
					if( volume < 0 ) volume = 0;
					break;
				case 0x70: /* Vol Slide Up.*/
					volume += noteVol & 0xF;
					if( volume > 64 ) volume = 64;
					break;
				case 0xB0: /* Vibrato.*/
					vibratoPhase += vibratoSpeed;
					vibrato( false );
					break;
				case 0xD0: /* Pan Slide Left.*/
					panning -= noteVol & 0xF;
					if( panning < 0 ) panning = 0;
					break;
				case 0xE0: /* Pan Slide Right.*/
					panning += noteVol & 0xF;
					if( panning > 255 ) panning = 255;
					break;
				case 0xF0: /* Tone Porta.*/
					tonePortamento();
					break;
			}
		}
		switch( noteEffect ) {
			case 0x01: case 0x86: /* Porta Up. */
				portamentoUp( portaUpParam );
				break;
			case 0x02: case 0x85: /* Porta Down. */
				portamentoDown( portaDownParam );
				break;
			case 0x03: case 0x87: /* Tone Porta. */
				tonePortamento();
				break;
			case 0x04: case 0x88: /* Vibrato. */
				vibratoPhase += vibratoSpeed;
				vibrato( false );
				break;
			case 0x05: case 0x8C: /* Tone Porta + Vol Slide. */
				tonePortamento();
				volumeSlide();
				break;
			case 0x06: case 0x8B: /* Vibrato + Vol Slide. */
				vibratoPhase += vibratoSpeed;
				vibrato( false );
				volumeSlide();
				break;
			case 0x07: case 0x92: /* Tremolo. */
				tremoloPhase += tremoloSpeed;
				tremolo();
				break;
			case 0x0A: case 0x84: /* Vol Slide. */
				volumeSlide();
				break;
			case 0x11: /* Global Volume Slide. */
				replay.globalVol += ( globalVslideParam >> 4 ) - ( globalVslideParam & 0xF );
				if( replay.globalVol < 0 ) replay.globalVol = 0;
				if( replay.globalVol > 64 ) replay.globalVol = 64;
				break;
			case 0x19: /* Panning Slide. */
				panning += ( panningSlideParam >> 4 ) - ( panningSlideParam & 0xF );
				if( panning < 0 ) panning = 0;
				if( panning > 255 ) panning = 255;
				break;
			case 0x1B: case 0x91: /* Retrig + Vol Slide. */
				retrigVolSlide();
				break;
			case 0x1D: case 0x89: /* Tremor. */
				tremor();
				break;
			case 0x79: /* Retrig. */
				if( fxCount >= noteParam ) {
					fxCount = 0;
					sampleIdx = 0;
				}
				break;
			case 0x7C: case 0xFC: /* Note Cut. */
				if( noteParam == fxCount ) volume = 0;
				break;
			case 0x7D: case 0xFD: /* Note Delay. */
				if( noteParam == fxCount ) trigger();
				break;
			case 0x8A: /* Arpeggio. */
				if( fxCount > 2 ) fxCount = 0;
				if( fxCount == 0 ) arpeggioAdd = 0;
				if( fxCount == 1 ) arpeggioAdd = arpeggioParam >> 4;
				if( fxCount == 2 ) arpeggioAdd = arpeggioParam & 0xF;
				break;
			case 0x95: /* Fine Vibrato. */
				vibratoPhase += vibratoSpeed;
				vibrato( true );
				break;
		}
		autoVibrato();
		calculateFrequency();
		calculateAmplitude();
		updateEnvelopes();
	}
	var updateEnvelopes = function() {
		if( instrument.volumeEnvelope.enabled ) {
			if( !keyOn ) {
				fadeOutVol -= instrument.volumeFadeOut;
				if( fadeOutVol < 0 ) fadeOutVol = 0;
			}
			volEnvTick = instrument.volumeEnvelope.nextTick( volEnvTick, keyOn );
		}
		if( instrument.panningEnvelope.enabled )
			panEnvTick = instrument.panningEnvelope.nextTick( panEnvTick, keyOn );
	}
	var autoVibrato = function() {
		var depth = instrument.vibratoDepth & 0x7F;
		if( depth > 0 ) {
			var sweep = instrument.vibratoSweep & 0x7F;
			var rate = instrument.vibratoRate & 0x7F;
			var type = instrument.vibratoType;
			if( autoVibratoCount < sweep ) depth = depth * autoVibratoCount / sweep;
			vibratoAdd += waveform( autoVibratoCount * rate >> 2, type + 4 ) * depth >> 8;
			autoVibratoCount++;
		}
	}
	var volumeSlide = function() {
		var up = vslideParam >> 4;
		var down = vslideParam & 0xF;
		if( down == 0xF && up > 0 ) { /* Fine slide up.*/
			if( fxCount == 0 ) volume += up;
		} else if( up == 0xF && down > 0 ) { /* Fine slide down.*/
			if( fxCount == 0 ) volume -= down;
		} else if( fxCount > 0 || replay.module.fastVolSlides ) /* Normal.*/
			volume += up - down;
		if( volume > 64 ) volume = 64;
		if( volume < 0 ) volume = 0;
	}
	var portamentoUp = function( param ) {
		switch( param & 0xF0 ) {
			case 0xE0: /* Extra-fine porta.*/
				if( fxCount == 0 ) period -= param & 0xF;
				break;
			case 0xF0: /* Fine porta.*/
				if( fxCount == 0 ) period -= ( param & 0xF ) << 2;
				break;
			default:/* Normal porta.*/
				if( fxCount > 0 ) period -= param << 2;
				break;
		}
		if( period < 0 ) period = 0;
	}
	var portamentoDown = function( param ) {
		if( period > 0 ) {
			switch( param & 0xF0 ) {
				case 0xE0: /* Extra-fine porta.*/
					if( fxCount == 0 ) period += param & 0xF;
					break;
				case 0xF0: /* Fine porta.*/
					if( fxCount == 0 ) period += ( param & 0xF ) << 2;
					break;
				default:/* Normal porta.*/
					if( fxCount > 0 ) period += param << 2;
					break;
			}
			if( period > 65535 ) period = 65535;
		}
	}
	var tonePortamento = function() {
		if( period > 0 ) {
			if( period < portaPeriod ) {
				period += tonePortaParam << 2;
				if( period > portaPeriod ) period = portaPeriod;
			} else {
				period -= tonePortaParam << 2;
				if( period < portaPeriod ) period = portaPeriod;
			}
		}
	}
	var vibrato = function( fine ) {
		vibratoAdd = waveform( vibratoPhase, vibratoType & 0x3 ) * vibratoDepth >> ( fine ? 7 : 5 );
	}
	var tremolo = function() {
		tremoloAdd = waveform( tremoloPhase, tremoloType & 0x3 ) * tremoloDepth >> 6;
	}
	var waveform = function( phase, type ) {
		var amplitude = 0;
		switch( type ) {
			default: /* Sine. */
				amplitude = sineTable[ phase & 0x1F ];
				if( ( phase & 0x20 ) > 0 ) amplitude = -amplitude;
				break;
			case 6: /* Saw Up.*/
				amplitude = ( ( ( phase + 0x20 ) & 0x3F ) << 3 ) - 255;
				break;
			case 1: case 7: /* Saw Down. */
				amplitude = 255 - ( ( ( phase + 0x20 ) & 0x3F ) << 3 );
				break;
			case 2: case 5: /* Square. */
				amplitude = ( phase & 0x20 ) > 0 ? 255 : -255;
				break;
			case 3: case 8: /* Random. */
				amplitude = ( randomSeed >> 20 ) - 255;
				randomSeed = ( randomSeed * 65 + 17 ) & 0x1FFFFFFF;
				break;
		}
		return amplitude;
	}
	var tremor = function() {
		if( retrigCount >= tremorOnTicks ) tremoloAdd = -64;
		if( retrigCount >= ( tremorOnTicks + tremorOffTicks ) )
			tremoloAdd = retrigCount = 0;
	}
	var retrigVolSlide = function() {
		if( retrigCount >= retrigTicks ) {
			retrigCount = sampleIdx = 0;
			switch( retrigVolume ) {
				case 0x1: volume = volume -  1; break;
				case 0x2: volume = volume -  2; break;
				case 0x3: volume = volume -  4; break;
				case 0x4: volume = volume -  8; break;
				case 0x5: volume = volume - 16; break;
				case 0x6: volume = ( volume * 2 / 3 ) | 0; break;
				case 0x7: volume = volume >> 1; break;
				case 0x8: /* ? */ break;
				case 0x9: volume = volume +  1; break;
				case 0xA: volume = volume +  2; break;
				case 0xB: volume = volume +  4; break;
				case 0xC: volume = volume +  8; break;
				case 0xD: volume = volume + 16; break;
				case 0xE: volume = ( volume * 3 / 2 ) | 0; break;
				case 0xF: volume = volume << 1; break;
			}
			if( volume <  0 ) volume = 0;
			if( volume > 64 ) volume = 64;
		}
	}
	var calculateFrequency = function() {
		var per = period + vibratoAdd;
		if( replay.module.linearPeriods ) {
			per = per - ( arpeggioAdd << 6 );
			if( per < 28 || per > 7680 ) per = 7680;
			freq = Math.round( replay.module.c2Rate * Math.pow( 2, ( 4608 - per ) / 768 ) );
		} else {
			per = per / Math.pow( 2, arpeggioAdd / 12 );
			if( per < 28 ) per = 29021;
			freq = Math.round( replay.module.c2Rate * 1712 / per );
		}
	}
	var calculateAmplitude = function() {
		var envVol = keyOn ? 64 : 0;
		if( instrument.volumeEnvelope.enabled )
			envVol = instrument.volumeEnvelope.calculateAmpl( volEnvTick );
		var vol = volume + tremoloAdd;
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		vol = vol * fadeOutVol / 0x8000;
		ampl = vol * envVol * replay.globalVol * replay.module.gain / 0x2000000;
		var envPan = 32;
		if( instrument.panningEnvelope.enabled )
			envPan = instrument.panningEnvelope.calculateAmpl( panEnvTick );
		var panRange = ( panning < 128 ) ? panning : ( 255 - panning );
		pann = ( panning + ( panRange * ( envPan - 32 ) >> 5 ) ) / 255;
	}
	var trigger = function() {
		if( noteIns > 0 && noteIns <= replay.module.numInstruments ) {
			instrument = replay.module.instruments[ noteIns ];
			var sam = instrument.samples[ instrument.keyToSample[ noteKey < 97 ? noteKey : 0 ] ];
			volume = sam.volume >= 64 ? 64 : sam.volume & 0x3F;
			if( sam.panning >= 0 ) panning = sam.panning & 0xFF;
			if( period > 0 && sam.loopLength > 1 ) sample = sam; /* Amiga trigger.*/
			sampleOffset = volEnvTick = panEnvTick = 0;
			fadeOutVol = 32768;
			keyOn = true;
		}
		if( noteEffect == 0x09 || noteEffect == 0x8F ) { /* Set Sample Offset. */
			if( noteParam > 0 ) offsetParam = noteParam;
			sampleOffset = offsetParam << 8;
		}
		if( noteVol >= 0x10 && noteVol < 0x60 )
			volume = noteVol < 0x50 ? noteVol - 0x10 : 64;
		switch( noteVol & 0xF0 ) {
			case 0x80: /* Fine Vol Down.*/
				volume -= noteVol & 0xF;
				if( volume < 0 ) volume = 0;
				break;
			case 0x90: /* Fine Vol Up.*/
				volume += noteVol & 0xF;
				if( volume > 64 ) volume = 64;
				break;
			case 0xA0: /* Set Vibrato Speed.*/
				if( ( noteVol & 0xF ) > 0 ) vibratoSpeed = noteVol & 0xF;
				break;
			case 0xB0: /* Vibrato.*/
				if( ( noteVol & 0xF ) > 0 ) vibratoDepth = noteVol & 0xF;
				vibrato( false );
				break;
			case 0xC0: /* Set Panning.*/
				panning = ( noteVol & 0xF ) * 17;
				break;
			case 0xF0: /* Tone Porta.*/
				if( ( noteVol & 0xF ) > 0 ) tonePortaParam = noteVol & 0xF;
				break;
		}
		if( noteKey > 0 ) {
			if( noteKey > 96 ) {
				keyOn = false;
			} else {
				var isPorta = ( noteVol & 0xF0 ) == 0xF0 ||
					noteEffect == 0x03 || noteEffect == 0x05 ||
					noteEffect == 0x87 || noteEffect == 0x8C;
				if( !isPorta ) sample = instrument.samples[ instrument.keyToSample[ noteKey ] ];
				var fineTune = sample.fineTune;
				if( noteEffect == 0x75 || noteEffect == 0xF2 ) { /* Set Fine Tune. */
					fineTune = ( noteParam & 0xF ) << 4;
					if( fineTune > 127 ) fineTune -= 256;
				}
				var key = noteKey + sample.relNote;
				if( key < 1 ) key = 1;
				if( key > 120 ) key = 120;
				var per = ( key << 6 ) + ( fineTune >> 1 );
				if( replay.module.linearPeriods ) {
					portaPeriod = 7744 - per;
				} else {
					portaPeriod = 29021 * Math.pow( 2, -per / 768 );
				}
				if( !isPorta ) {
					period = portaPeriod;
					sampleIdx = sampleOffset;
					if( vibratoType < 4 ) vibratoPhase = 0;
					if( tremoloType < 4 ) tremoloPhase = 0;
					retrigCount = autoVibratoCount = 0;
				}
			}
		}
	}
}

function IBXMNote() {
	this.key = 0;
	this.instrument = 0;
	this.volume = 0;
	this.effect = 0;
	this.param = 0;
}

function IBXMPattern( numChannels, numRows ) {
	this.numRows = numRows;
	this.data = new Int8Array( numChannels * numRows * 5 );
	this.getNote = function( index, note ) {
		var offset = index * 5;
		note.key = this.data[ offset ] & 0xFF;
		note.instrument = this.data[ offset + 1 ] & 0xFF;
		note.volume = this.data[ offset + 2 ] & 0xFF;
		note.effect = this.data[ offset + 3 ] & 0xFF;
		note.param = this.data[ offset + 4 ] & 0xFF;
	}
}

function IBXMInstrument() {
	this.name = "";
	this.numSamples = 1;
	this.vibratoType = 0;
	this.vibratoSweep = 0;
	this.vibratoDepth = 0;
	this.vibratoRate = 0;
	this.volumeFadeOut = 0;
	this.volumeEnvelope = new IBXMEnvelope();
	this.panningEnvelope = new IBXMEnvelope();
	this.keyToSample = new Int8Array( 97 );
	this.samples = [ new IBXMSample() ];
}

function IBXMEnvelope() {
	this.enabled = false;
	this.sustain = false;
	this.looped = false;
	this.sustainTick = 0;
	this.loopStartTick = 0;
	this.loopEndTick = 0;
	this.numPoints = 1;
	this.pointsTick = new Int32Array( 1 );
	this.pointsAmpl = new Int32Array( 1 );
	this.nextTick = function( tick, keyOn ) {
		tick++;
		if( this.looped && tick >= this.loopEndTick ) tick = this.loopStartTick;
		if( this.sustain && keyOn && tick >= this.sustainTick ) tick = this.sustainTick;
		return tick;
	}
	this.calculateAmpl = function( tick ) {
		var ampl = this.pointsAmpl[ this.numPoints - 1 ];
		if( tick < this.pointsTick[ this.numPoints - 1 ] ) {
			var point = 0;
			for( var idx = 1; idx < this.numPoints; idx++ )
				if( this.pointsTick[ idx ] <= tick ) point = idx;
			var dt = this.pointsTick[ point + 1 ] - this.pointsTick[ point ];
			var da = this.pointsAmpl[ point + 1 ] - this.pointsAmpl[ point ];
			ampl = this.pointsAmpl[ point ];
			ampl += ( da / dt ) * ( tick - this.pointsTick[ point ] );
		}
		return ampl;
	}
}

function IBXMSample() {
	this.name = "";
	this.volume = 0;
	this.panning = -1;
	this.relNote = 0;
	this.fineTune = 0;
	this.loopStart = 0;
	this.loopLength = 0;
	this.sampleData = new Int16Array( 1 );
	this.setSampleData = function( sampleData, loopStart, loopLength, pingPong ) {
		var sampleLength = sampleData.length;
		/* Fix loop if necessary.*/
		if( loopStart < 0 || loopStart > sampleLength )
			loopStart = sampleLength;
		if( loopLength < 0 || ( loopStart + loopLength ) > sampleLength )
			loopLength = sampleLength - loopStart;
		sampleLength = loopStart + loopLength;
		/* Allocate new sample.*/
		var newSampleData = new Int16Array( sampleLength + ( pingPong ? loopLength : 0 ) + 1 );
		newSampleData.set( sampleData.subarray( 0, sampleLength ) );
		sampleData = newSampleData;
		if( pingPong ) {
			/* Calculate reversed loop.*/
			for( var idx = 0; idx < loopLength; idx++ )
				sampleData[ sampleLength + idx ] = sampleData[ sampleLength - idx - 1 ];
			loopLength *= 2;
		}
		/* Extend loop for linear interpolation.*/
		sampleData[ loopStart + loopLength ] = sampleData[ loopStart ];
		this.sampleData = sampleData;
		this.loopStart = loopStart;
		this.loopLength = loopLength;
	}
}

function IBXMData( buffer ) {
	this.sByte = function( offset ) {
		return buffer[ offset ] | 0;
	}
	this.uByte = function( offset ) {
		return buffer[ offset ] & 0xFF;
	}
	this.ubeShort = function( offset ) {
		return ( ( buffer[ offset ] & 0xFF ) << 8 ) | ( buffer[ offset + 1 ] & 0xFF );
	}
	this.uleShort = function( offset ) {
		return ( buffer[ offset ] & 0xFF ) | ( ( buffer[ offset + 1 ] & 0xFF ) << 8 );
	}
	this.uleInt = function( offset ) {
		var value = buffer[ offset ] & 0xFF;
		value = value | ( ( buffer[ offset + 1 ] & 0xFF ) << 8 );
		value = value | ( ( buffer[ offset + 2 ] & 0xFF ) << 16 );
		value = value | ( ( buffer[ offset + 3 ] & 0x7F ) << 24 );
		return value;
	}
	this.strLatin1 = function( offset, length ) {
		var str = new Array( length );
		for( var idx = 0; idx < length; idx++ ) {
			var chr = buffer[ offset + idx ] & 0xFF;
			str[ idx ] = String.fromCharCode( chr < 32 ? 32 : chr );
		}
		return str.join('');
	}
	this.samS8 = function( offset, length ) {
		var sampleData = new Int16Array( length );
		for( var idx = 0; idx < length; idx++ ) {
			sampleData[ idx ] = buffer[ offset + idx ] << 8;
		}
		return sampleData;
	}
	this.samS8D = function( offset, length ) {
		var sampleData = new Int16Array( length );
		var sam = 0;
		for( var idx = 0; idx < length; idx++ ) {
			sam += buffer[ offset + idx ] | 0;
			sampleData[ idx ] = sam << 8;
		}
		return sampleData;
	}
	this.samU8 = function( offset, length ) {
		var sampleData = new Int16Array( length );
		for( var idx = 0; idx < length; idx++ ) {
			sampleData[ idx ] = ( ( buffer[ offset + idx ] & 0xFF ) - 128 ) << 8;
		}
		return sampleData;
	}
	this.samS16 = function( offset, samples ) {
		var sampleData = new Int16Array( samples );
		for( var idx = 0; idx < samples; idx++ ) {
			sampleData[ idx ] = ( buffer[ offset + idx * 2 ] & 0xFF ) | ( buffer[ offset + idx * 2 + 1 ] << 8 );
		}
		return sampleData;
	}
	this.samS16D = function( offset, samples ) {
		var sampleData = new Int16Array( samples );
		var sam = 0;
		for( var idx = 0; idx < samples; idx++ ) {
			sam += ( buffer[ offset + idx * 2 ] & 0xFF ) | ( buffer[ offset + idx * 2 + 1 ] << 8 );
			sampleData[ idx ] = sam;
		}
		return sampleData;
	}
	this.samU16 = function( offset, samples ) {
		var sampleData = new Int16Array( samples );
		for( var idx = 0; idx < samples; idx++ ) {
			var sam = ( buffer[ offset + idx * 2 ] & 0xFF ) | ( ( buffer[ offset + idx * 2 + 1 ] & 0xFF ) << 8 );
			sampleData[ idx ] = sam - 32768;
		}
		return sampleData;
	}
}

function IBXMModule( moduleData ) {
	this.songName = "Blank";
	this.numChannels = 4;
	this.numInstruments = 1;
	this.numPatterns = 1;
	this.sequenceLength = 1;
	this.restartPos = 0;
	this.defaultGVol = 64;
	this.defaultSpeed = 6;
	this.defaultTempo = 125;
	this.c2Rate = 8287;
	this.gain = 64;
	this.linearPeriods = false;
	this.fastVolSlides = false;
	this.defaultPanning = new Int32Array( [ 51, 204, 204, 51 ] );
	this.sequence = new Int32Array( 1 );
	this.patterns = [ new IBXMPattern( 4, 64 ) ];
	this.instruments = [ new IBXMInstrument(), new IBXMInstrument() ];
	this.loadXM = function( ibxmData ) {
		if( ibxmData.uleShort( 58 ) != 0x0104 )
			throw "XM format version must be 0x0104!";
		this.songName = ibxmData.strLatin1( 17, 20 );
		var deltaEnv = ibxmData.strLatin1( 38, 20 ).startsWith( "DigiBooster Pro" );
		var dataOffset = 60 + ibxmData.uleInt( 60 );
		this.sequenceLength = ibxmData.uleShort( 64 );
		this.restartPos = ibxmData.uleShort( 66 );
		this.numChannels = ibxmData.uleShort( 68 );
		this.numPatterns = ibxmData.uleShort( 70 );
		this.numInstruments = ibxmData.uleShort( 72 );
		this.linearPeriods = ( ibxmData.uleShort( 74 ) & 0x1 ) > 0;
		this.defaultGVol = 64;
		this.defaultSpeed = ibxmData.uleShort( 76 );
		this.defaultTempo = ibxmData.uleShort( 78 );
		this.c2Rate = 8363;
		this.gain = 64;
		this.defaultPanning = new Int32Array( this.numChannels );
		for( var idx = 0; idx < this.numChannels; idx++ ) this.defaultPanning[ idx ] = 128;
		this.sequence = new Int32Array( this.sequenceLength );
		for( var seqIdx = 0; seqIdx < this.sequenceLength; seqIdx++ ) {
			var entry = ibxmData.uByte( 80 + seqIdx );
			this.sequence[ seqIdx ] = entry < this.numPatterns ? entry : 0;
		}
		this.patterns = new Array( this.numPatterns );
		for( var patIdx = 0; patIdx < this.numPatterns; patIdx++ ) {
			if( ibxmData.uByte( dataOffset + 4 ) != 0 )
				throw "Unknown pattern packing type!";
			var numRows = ibxmData.uleShort( dataOffset + 5 );
			var numNotes = numRows * this.numChannels;
			var pattern = this.patterns[ patIdx ] = new IBXMPattern( this.numChannels, numRows );
			var patternDataLength = ibxmData.uleShort( dataOffset + 7 );
			dataOffset += ibxmData.uleInt( dataOffset );
			var nextOffset = dataOffset + patternDataLength;
			if( patternDataLength > 0 ) {
				var patternDataOffset = 0;
				for( var note = 0; note < numNotes; note++ ) {
					var flags = ibxmData.uByte( dataOffset );
					if( ( flags & 0x80 ) == 0 ) flags = 0x1F; else dataOffset++;
					var key = ( flags & 0x01 ) > 0 ? ibxmData.sByte( dataOffset++ ) : 0;
					pattern.data[ patternDataOffset++ ] = key;
					var ins = ( flags & 0x02 ) > 0 ? ibxmData.sByte( dataOffset++ ) : 0;
					pattern.data[ patternDataOffset++ ] = ins;
					var vol = ( flags & 0x04 ) > 0 ? ibxmData.sByte( dataOffset++ ) : 0;
					pattern.data[ patternDataOffset++ ] = vol;
					var fxc = ( flags & 0x08 ) > 0 ? ibxmData.sByte( dataOffset++ ) : 0;
					var fxp = ( flags & 0x10 ) > 0 ? ibxmData.sByte( dataOffset++ ) : 0;
					if( fxc >= 0x40 ) fxc = fxp = 0;
					pattern.data[ patternDataOffset++ ] = fxc;
					pattern.data[ patternDataOffset++ ] = fxp;
				}
			}
			dataOffset = nextOffset;
		}
		this.instruments = new Array( this.numInstruments + 1 );
		this.instruments[ 0 ] = new IBXMInstrument();
		for( var insIdx = 1; insIdx <= this.numInstruments; insIdx++ ) {
			var instrument = this.instruments[ insIdx ] = new IBXMInstrument();
			instrument.name = ibxmData.strLatin1( dataOffset + 4, 22 );
			var numSamples = instrument.numSamples = ibxmData.uleShort( dataOffset + 27 );
			if( numSamples > 0 ) {
				instrument.samples = new Array( numSamples );
				for( var keyIdx = 0; keyIdx < 96; keyIdx++ )
					instrument.keyToSample[ keyIdx + 1 ] = ibxmData.uByte( dataOffset + 33 + keyIdx );
				var volEnv = instrument.volumeEnvelope = new IBXMEnvelope();
				volEnv.pointsTick = new Int32Array( 16 );
				volEnv.pointsAmpl = new Int32Array( 16 );
				var pointTick = 0;
				for( var point = 0; point < 12; point++ ) {
					var pointOffset = dataOffset + 129 + ( point * 4 );
					pointTick = ( deltaEnv ? pointTick : 0 ) + ibxmData.uleShort( pointOffset );
					volEnv.pointsTick[ point ] = pointTick;
					volEnv.pointsAmpl[ point ] = ibxmData.uleShort( pointOffset + 2 );
				}
				var panEnv = instrument.panningEnvelope = new IBXMEnvelope();
				panEnv.pointsTick = new Int32Array( 16 );
				panEnv.pointsAmpl = new Int32Array( 16 );
				pointTick = 0;
				for( var point = 0; point < 12; point++ ) {
					var pointOffset = dataOffset + 177 + ( point * 4 );
					pointTick = ( deltaEnv ? pointTick : 0 ) + ibxmData.uleShort( pointOffset );
					panEnv.pointsTick[ point ] = pointTick;
					panEnv.pointsAmpl[ point ] = ibxmData.uleShort( pointOffset + 2 );
				}
				volEnv.numPoints = ibxmData.uByte( dataOffset + 225 );
				if( volEnv.numPoints > 12 ) volEnv.numPoints = 0;
				panEnv.numPoints = ibxmData.uByte( dataOffset + 226 );
				if( panEnv.numPoints > 12 ) panEnv.numPoints = 0;
				volEnv.sustainTick = volEnv.pointsTick[ ibxmData.uByte( dataOffset + 227 ) & 0xF ];
				volEnv.loopStartTick = volEnv.pointsTick[ ibxmData.uByte( dataOffset + 228 ) & 0xF ];
				volEnv.loopEndTick = volEnv.pointsTick[ ibxmData.uByte( dataOffset + 229 ) & 0xF ];
				panEnv.sustainTick = panEnv.pointsTick[ ibxmData.uByte( dataOffset + 230 ) & 0xF ];
				panEnv.loopStartTick = panEnv.pointsTick[ ibxmData.uByte( dataOffset + 231 ) & 0xF ];
				panEnv.loopEndTick = panEnv.pointsTick[ ibxmData.uByte( dataOffset + 232 ) & 0xF ];
				volEnv.enabled = volEnv.numPoints > 0 && ( ibxmData.uByte( dataOffset + 233 ) & 0x1 ) > 0;
				volEnv.sustain = ( ibxmData.uByte( dataOffset + 233 ) & 0x2 ) > 0;
				volEnv.looped = ( ibxmData.uByte( dataOffset + 233 ) & 0x4 ) > 0;
				panEnv.enabled = panEnv.numPoints > 0 && ( ibxmData.uByte( dataOffset + 234 ) & 0x1 ) > 0;
				panEnv.sustain = ( ibxmData.uByte( dataOffset + 234 ) & 0x2 ) > 0;
				panEnv.looped = ( ibxmData.uByte( dataOffset + 234 ) & 0x4 ) > 0;
				instrument.vibratoType = ibxmData.uByte( dataOffset + 235 );
				instrument.vibratoSweep = ibxmData.uByte( dataOffset + 236 );
				instrument.vibratoDepth = ibxmData.uByte( dataOffset + 237 );
				instrument.vibratoRate = ibxmData.uByte( dataOffset + 238 );
				instrument.volumeFadeOut = ibxmData.uleShort( dataOffset + 239 );
			}
			dataOffset += ibxmData.uleInt( dataOffset );
			var sampleHeaderOffset = dataOffset;
			dataOffset += numSamples * 40;
			for( var samIdx = 0; samIdx < numSamples; samIdx++ ) {
				var sample = instrument.samples[ samIdx ] = new IBXMSample();
				var sampleDataBytes = ibxmData.uleInt( sampleHeaderOffset );
				var sampleLoopStart = ibxmData.uleInt( sampleHeaderOffset + 4 );
				var sampleLoopLength = ibxmData.uleInt( sampleHeaderOffset + 8 );
				sample.volume = ibxmData.sByte( sampleHeaderOffset + 12 );
				sample.fineTune = ibxmData.sByte( sampleHeaderOffset + 13 );
				var looped = ( ibxmData.uByte( sampleHeaderOffset + 14 ) & 0x3 ) > 0;
				var pingPong = ( ibxmData.uByte( sampleHeaderOffset + 14 ) & 0x2 ) > 0;
				var sixteenBit = ( ibxmData.uByte( sampleHeaderOffset + 14 ) & 0x10 ) > 0;
				sample.panning = ibxmData.uByte( sampleHeaderOffset + 15 );
				sample.relNote = ibxmData.sByte( sampleHeaderOffset + 16 );
				sample.name = ibxmData.strLatin1( sampleHeaderOffset + 18, 22 );
				sampleHeaderOffset += 40;
				if( !looped || ( sampleLoopStart + sampleLoopLength ) > sampleDataBytes ) {
					sampleLoopStart = sampleDataBytes;
					sampleLoopLength = 0;
				}
				if( sixteenBit ) {
					sample.setSampleData( ibxmData.samS16D( dataOffset, sampleDataBytes >> 1 ), sampleLoopStart >> 1, sampleLoopLength >> 1, pingPong );
				} else {
					sample.setSampleData( ibxmData.samS8D( dataOffset, sampleDataBytes ), sampleLoopStart, sampleLoopLength, pingPong );
				}
				dataOffset += sampleDataBytes;
			}
		}
	}
	this.loadS3M = function( ibxmData ) {
		this.songName = ibxmData.strLatin1( 0, 28 );
		this.sequenceLength = ibxmData.uleShort( 32 );
		this.numInstruments = ibxmData.uleShort( 34 );
		this.numPatterns = ibxmData.uleShort( 36 );
		var flags = ibxmData.uleShort( 38 );
		var version = ibxmData.uleShort( 40 );
		this.fastVolSlides = ( ( flags & 0x40 ) == 0x40 ) || version == 0x1300;
		var signedSamples = ibxmData.uleShort( 42 ) == 1;
		if( ibxmData.uleInt( 44 ) != 0x4d524353 ) throw "Not an S3M file!";
		this.defaultGVol = ibxmData.uByte( 48 );
		this.defaultSpeed = ibxmData.uByte( 49 );
		this.defaultTempo = ibxmData.uByte( 50 );
		this.c2Rate = 8363;
		this.gain = ibxmData.uByte( 51 ) & 0x7F;
		var stereoMode = ( ibxmData.uByte( 51 ) & 0x80 ) == 0x80;
		var defaultPan = ibxmData.uByte( 53 ) == 0xFC;
		var channelMap = new Int32Array( 32 );
		this.numChannels = 0;
		for( var chanIdx = 0; chanIdx < 32; chanIdx++ ) {
			channelMap[ chanIdx ] = -1;
			if( ibxmData.uByte( 64 + chanIdx ) < 16 )
				channelMap[ chanIdx ] = this.numChannels++;
		}
		this.sequence = new Int32Array( this.sequenceLength );
		for( var seqIdx = 0; seqIdx < this.sequenceLength; seqIdx++ )
			this.sequence[ seqIdx ] = ibxmData.uByte( 96 + seqIdx );
		var moduleDataIdx = 96 + this.sequenceLength;
		this.instruments = new Array( this.numInstruments + 1 );
		this.instruments[ 0 ] = new IBXMInstrument();
		for( var instIdx = 1; instIdx <= this.numInstruments; instIdx++ ) {
			var instrument = this.instruments[ instIdx ] = new IBXMInstrument();
			var sample = instrument.samples[ 0 ];
			var instOffset = ibxmData.uleShort( moduleDataIdx ) << 4;
			moduleDataIdx += 2;
			instrument.name = ibxmData.strLatin1( instOffset + 48, 28 );
			if( ibxmData.uByte( instOffset ) != 1 ) continue;
			if( ibxmData.uleShort( instOffset + 76 ) != 0x4353 ) continue;
			var sampleOffset = ibxmData.uByte( instOffset + 13 ) << 20;
			sampleOffset += ibxmData.uleShort( instOffset + 14 ) << 4;
			var sampleLength = ibxmData.uleInt( instOffset + 16 );
			var loopStart = ibxmData.uleInt( instOffset + 20 );
			var loopLength = ibxmData.uleInt( instOffset + 24 ) - loopStart;
			sample.volume = ibxmData.uByte( instOffset + 28 );
			sample.panning = -1;
			var packed = ibxmData.uByte( instOffset + 30 ) != 0;
			var loopOn = ( ibxmData.uByte( instOffset + 31 ) & 0x1 ) == 0x1;
			if( loopStart + loopLength > sampleLength )
				loopLength = sampleLength - loopStart;
			if( loopLength < 1 || !loopOn ) {
				loopStart = sampleLength;
				loopLength = 0;
			}
			var stereo = ( ibxmData.uByte( instOffset + 31 ) & 0x2 ) == 0x2;
			var sixteenBit = ( ibxmData.uByte( instOffset + 31 ) & 0x4 ) == 0x4;
			if( packed ) throw "Packed samples not supported!";
			var c2Rate = ibxmData.uleInt( instOffset + 32 );
			var tune = ( Math.log( c2Rate ) - Math.log( this.c2Rate ) ) * 12 / Math.log( 2 );
			sample.relNote = Math.round( tune );
			sample.fineTune = Math.round( ( tune - sample.relNote ) * 128 );
			if( sixteenBit ) {
				if( signedSamples ) {
					sample.setSampleData( ibxmData.samS16( sampleOffset, sampleLength ), loopStart, loopLength, false );
				} else {
					sample.setSampleData( ibxmData.samU16( sampleOffset, sampleLength ), loopStart, loopLength, false );
				}
			} else {
				if( signedSamples ) {
					sample.setSampleData( ibxmData.samS8( sampleOffset, sampleLength ), loopStart, loopLength, false );
				} else {
					sample.setSampleData( ibxmData.samU8( sampleOffset, sampleLength ), loopStart, loopLength, false );
				}
			}
		}
		this.patterns = new Array( this.numPatterns );
		for( var patIdx = 0; patIdx < this.numPatterns; patIdx++ ) {
			var pattern = this.patterns[ patIdx ] = new IBXMPattern( this.numChannels, 64 );
			var inOffset = ( ibxmData.uleShort( moduleDataIdx ) << 4 ) + 2;
			var rowIdx = 0;
			while( rowIdx < 64 ) {
				var token = ibxmData.uByte( inOffset++ );
				if( token == 0 ) {
					rowIdx++;
					continue;
				}
				var noteKey = 0;
				var noteIns = 0;
				if( ( token & 0x20 ) == 0x20 ) { /* Key + Instrument.*/
					noteKey = ibxmData.uByte( inOffset++ );
					noteIns = ibxmData.uByte( inOffset++ );
					if( noteKey < 0xFE )
						noteKey = ( noteKey >> 4 ) * 12 + ( noteKey & 0xF ) + 1;
					if( noteKey == 0xFF ) noteKey = 0;
				}
				var noteVol = 0;
				if( ( token & 0x40 ) == 0x40 ) { /* Volume Column.*/
					noteVol = ( ibxmData.uByte( inOffset++ ) & 0x7F ) + 0x10;
					if( noteVol > 0x50 ) noteVol = 0;
				}
				var noteEffect = 0;
				var noteParam = 0;
				if( ( token & 0x80 ) == 0x80 ) { /* Effect + Param.*/
					noteEffect = ibxmData.uByte( inOffset++ );
					noteParam = ibxmData.uByte( inOffset++ );
					if( noteEffect < 1 || noteEffect >= 0x40 )
						noteEffect = noteParam = 0;
					if( noteEffect > 0 ) noteEffect += 0x80;
				}
				var chanIdx = channelMap[ token & 0x1F ];
				if( chanIdx >= 0 ) {
					var noteOffset = ( rowIdx * this.numChannels + chanIdx ) * 5;
					pattern.data[ noteOffset     ] = noteKey;
					pattern.data[ noteOffset + 1 ] = noteIns;
					pattern.data[ noteOffset + 2 ] = noteVol;
					pattern.data[ noteOffset + 3 ] = noteEffect;
					pattern.data[ noteOffset + 4 ] = noteParam;
				}
			}
			moduleDataIdx += 2;
		}
		this.defaultPanning = new Int32Array( this.numChannels );
		for( var chanIdx = 0; chanIdx < 32; chanIdx++ ) {
			if( channelMap[ chanIdx ] < 0 ) continue;
			var panning = 7;
			if( stereoMode ) {
				panning = 12;
				if( ibxmData.uByte( 64 + chanIdx ) < 8 ) panning = 3;
			}
			if( defaultPan ) {
				var panFlags = ibxmData.uByte( moduleDataIdx + chanIdx );
				if( ( panFlags & 0x20 ) == 0x20 ) panning = panFlags & 0xF;
			}
			this.defaultPanning[ channelMap[ chanIdx ] ] = panning * 17;
		}
	}
	this.loadMod = function( ibxmData ) {
		this.songName = ibxmData.strLatin1( 0, 20 );
		this.sequenceLength = ibxmData.uByte( 950 ) & 0x7F;
		this.restartPos = ibxmData.uByte( 951 ) & 0x7F;
		if( this.restartPos >= this.sequenceLength ) this.restartPos = 0;
		this.sequence = new Int32Array( 128 );
		for( var seqIdx = 0; seqIdx < 128; seqIdx++ ) {
			var patIdx = ibxmData.uByte( 952 + seqIdx ) & 0x7F;
			this.sequence[ seqIdx ] = patIdx;
			if( patIdx >= this.numPatterns ) this.numPatterns = patIdx + 1;
		}
		switch( ibxmData.ubeShort( 1082 ) ) {
			case 0x4b2e: /* M.K. */
			case 0x4b21: /* M!K! */
			case 0x5434: /* FLT4 */
				this.numChannels = 4;
				this.c2Rate = 8287; /* PAL */
				this.gain = 64;
				break;
			case 0x484e: /* xCHN */
				this.numChannels = ibxmData.uByte( 1080 ) - 48;
				this.c2Rate = 8363; /* NTSC */
				this.gain = 32;
				break;
			case 0x4348: /* xxCH */
				this.numChannels  = ( ibxmData.uByte( 1080 ) - 48 ) * 10;
				this.numChannels += ibxmData.uByte( 1081 ) - 48;
				this.c2Rate = 8363; /* NTSC */
				this.gain = 32;
				break;
			default:
				throw "MOD Format not recognised!";
		}
		this.defaultGVol = 64;
		this.defaultSpeed = 6;
		this.defaultTempo = 125;
		this.defaultPanning = new Int32Array( this.numChannels );
		for( var idx = 0; idx < this.numChannels; idx++ ) {
			this.defaultPanning[ idx ] = 51;
			if( ( idx & 3 ) == 1 || ( idx & 3 ) == 2 )
				this.defaultPanning[ idx ] = 204;
		}
		var moduleDataIdx = 1084;
		this.patterns = new Array( this.numPatterns );
		for( var patIdx = 0; patIdx < this.numPatterns; patIdx++ ) {
			var pattern = this.patterns[ patIdx ] = new IBXMPattern( this.numChannels, 64 );
			for( var patDataIdx = 0; patDataIdx < pattern.data.length; patDataIdx += 5 ) {
				var period = ( ibxmData.uByte( moduleDataIdx ) & 0xF ) << 8;
				period = ( period | ibxmData.uByte( moduleDataIdx + 1 ) ) * 4;
				if( period > 112 ) {
					var key = Math.round( -12 * Math.log( period / 29021 ) / Math.log( 2 ) );
					pattern.data[ patDataIdx ] = key;
				}
				var ins = ( ibxmData.uByte( moduleDataIdx + 2 ) & 0xF0 ) >> 4;
				ins = ins | ibxmData.uByte( moduleDataIdx ) & 0x10;
				pattern.data[ patDataIdx + 1 ] = ins;
				var effect = ibxmData.uByte( moduleDataIdx + 2 ) & 0x0F;
				var param  = ibxmData.uByte( moduleDataIdx + 3 );
				if( param == 0 && ( effect < 3 || effect == 0xA ) ) effect = 0;
				if( param == 0 && ( effect == 5 || effect == 6 ) ) effect -= 2;
				if( effect == 8 && this.numChannels == 4 ) effect = param = 0;
				pattern.data[ patDataIdx + 3 ] = effect;
				pattern.data[ patDataIdx + 4 ] = param;
				moduleDataIdx += 4;
			}
		}
		this.numInstruments = 31;
		this.instruments = new Array( this.numInstruments + 1 );
		this.instruments[ 0 ] = new IBXMInstrument();
		for( var instIdx = 1; instIdx <= this.numInstruments; instIdx++ ) {
			var instrument = this.instruments[ instIdx ] = new IBXMInstrument();
			var sample = instrument.samples[ 0 ];
			instrument.name = ibxmData.strLatin1( instIdx * 30 - 10, 22 );
			var sampleLength = ibxmData.ubeShort( instIdx * 30 + 12 ) * 2;
			var fineTune = ( ibxmData.uByte( instIdx * 30 + 14 ) & 0xF ) << 4;
			sample.fineTune = ( fineTune < 128 ) ? fineTune : fineTune - 256;
			var volume = ibxmData.uByte( instIdx * 30 + 15 ) & 0x7F;
			sample.volume = ( volume <= 64 ) ? volume : 64;
			sample.panning = -1;
			var loopStart = ibxmData.ubeShort( instIdx * 30 + 16 ) * 2;
			var loopLength = ibxmData.ubeShort( instIdx * 30 + 18 ) * 2;
			if( loopStart + loopLength > sampleLength )
				loopLength = sampleLength - loopStart;
			if( loopLength < 4 ) {
				loopStart = sampleLength;
				loopLength = 0;
			}
			sample.setSampleData( ibxmData.samS8( moduleDataIdx, sampleLength ), loopStart, loopLength, false );
			moduleDataIdx += sampleLength;
		}
	}
	if( moduleData != undefined ) {
		var ibxmData = new IBXMData( moduleData );
		if( ibxmData.strLatin1( 0, 17 ) == "Extended Module: " ) {
			this.loadXM( ibxmData );
		} else if( ibxmData.strLatin1( 44, 4 ) == "SCRM" ) {
			this.loadS3M( ibxmData );
		} else {
			this.loadMod( ibxmData );
		}
	}
}

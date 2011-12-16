
/*
	JavaScript ProTracker Replay (c)2011 mumart@gmail.com
*/
function Micromod( module, samplingRate ) {
	/* Return a String representing the version of the replay. */
	this.getVersion = function() {
		return "20111216 (c)2011 mumart@gmail.com";
	}

	/* Return the sampling rate of playback. */
	this.getSamplingRate = function() {
		return samplingRate;
	}

	/* Enable or disable the linear interpolation filter. */
	this.setInterpolation = function( interp ) {
		interpolation = interp;
	}

	/* Set the pattern in the sequence to play. The tempo is reset to the default. */
	this.setSequencePos = function( pos ) {
		if( pos >= module.sequenceLength ) {
			pos = 0;
		}
		breakSeqPos = pos;
		nextRow = 0;
		tick = 1;
		speed = 6;
		setTempo( 125 );
		plCount = plChannel = -1;
		for( var idx = 0; idx < module.numChannels; idx++ ) {
			channels[ idx ] = new Channel( module, idx, samplingRate * OVERSAMPLE );
		}
		for( var idx = 0, end = rampLen * 2; idx < end; idx++ ) {
			rampBuf[ idx ] = 0;
		}
		filtL = filtR = 0;
		seqTick();
	}

	/* Returns the song duration in samples at the current sampling rate. */
	this.calculateSongDuration = function() {
		var duration = 0;
		setSequencePos( 0 );
		var songEnd = false;
		while( !songEnd ) {
			duration += ( tickLen / OVERSAMPLE ) | 0;
			songEnd = seqTick();
		}
		setSequencePos( 0 );
		return duration;	
	}

	/*
		Seek to approximately the specified sample position.
		The actual sample position reached is returned.
	*/
	this.seek = function( samplePos ) {
		setSequencePos( 0 );
		var currentPos = 0;
		while( ( samplePos - currentPos ) >= tickLen ) {
			for( var idx = 0; idx < module.numChannels; idx++ ) {
				channels[ idx ].updateSampleIdx( tickLen );
			}
			currentPos += ( tickLen / OVERSAMPLE ) | 0;
			seqTick();
		}
		return currentPos;
	}

	/* Write count floating-point stereo samples into outputBuf. */
	this.getAudio = function( outputBuf, count ) {
		var outIdx = 0;
		while( outIdx < count ) {
			if( mixIdx >= mixLen ) {
				// Calculate next tick.
				for( var idx = 0, end = ( tickLen + rampLen ) << 1; idx < end; idx++ ) {
					// Clear mix buffer.
					mixBuf[ idx ] = 0;
				}
				for( var idx = 0; idx < module.numChannels; idx++ ) {
					// Resample and mix each channel.
					var chan = channels[ idx ];
					chan.resample( mixBuf, 0, tickLen + rampLen, interpolation );
					chan.updateSampleIdx( tickLen );
				}
				volumeRamp( mixBuf );
				mixLen = downsample( mixBuf, tickLen );
				mixIdx = 0;
				// Update the sequencer.
				seqTick();
			}
			var remain = mixLen - mixIdx;
			if( ( outIdx + remain ) > count ) {
				remain = count - outIdx;
			}
			for( var idx = outIdx << 1, end = ( outIdx + remain ) << 1, mix = mixIdx << 1; idx < end; ) {
				// Convert to floating-point and divide by ~32768 for output.
				outputBuf[ idx++ ] = mixBuf[ mix++ ] * 0.0000305;
			}
			mixIdx += remain;
			outIdx += remain;
		}
	}

	var setTempo = function( tempo ) {
		// Make sure tick length is even to simplify downsampling.
		tickLen = ( ( samplingRate * OVERSAMPLE * 5 ) / ( tempo * 2 ) ) & -2;
	}

	var volumeRamp = function( mixBuf ) {
		var a1, a2, s1, s2, offset = 0;
		for( a1 = 0; a1 < 256; a1 += rampRate ) {
			a2 = 256 - a1;
			s1 =  mixBuf[ offset ] * a1;
			s2 = rampBuf[ offset ] * a2;
			mixBuf[ offset++ ] = ( s1 + s2 ) >> 8;
			s1 =  mixBuf[ offset ] * a1;
			s2 = rampBuf[ offset ] * a2;
			mixBuf[ offset++ ] = ( s1 + s2 ) >> 8;
		}
		//System.arraycopy( mixBuf, tickLen << 1, rampBuf, 0, offset );
		rampBuf.set( mixBuf.subarray( tickLen << 1, ( tickLen + rampLen ) << 1 ) );
	}

	var downsample = function( buf, count ) {
		// 2:1 downsampling with simple but effective anti-aliasing.
		// Count is the number of stereo samples to process, and must be even.
		var fl = filtL, fr = filtR;
		var inIdx = 0, outIdx = 0;
		while( outIdx < count ) {	
			var outL = fl + ( buf[ inIdx++ ] >> 1 );
			var outR = fr + ( buf[ inIdx++ ] >> 1 );
			fl = buf[ inIdx++ ] >> 2;
			fr = buf[ inIdx++ ] >> 2;
			buf[ outIdx++ ] = outL + fl;
			buf[ outIdx++ ] = outR + fr;
		}
		filtL = fl;
		filtR = fr;
		return count >> 1;
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
		row = nextRow;
		nextRow = row + 1;
		if( nextRow >= 64 ) {
			breakSeqPos = seqPos + 1;
			nextRow = 0;
		}
		var patOffset = ( module.sequence[ seqPos ] * 64 + row ) * module.numChannels * 4;
		for( var chanIdx = 0; chanIdx < module.numChannels; chanIdx++ ) {
			var channel = channels[ chanIdx ];
			var key = ( module.patterns[ patOffset ] & 0xF ) << 8;
			key = key | module.patterns[ patOffset + 1 ] & 0xFF;
			var ins = ( module.patterns[ patOffset + 2 ] & 0xF0 ) >> 4;
			ins = ins | module.patterns[ patOffset ] & 0x10;
			var effect = module.patterns[ patOffset + 2 ] & 0x0F;
			var param  = module.patterns[ patOffset + 3 ] & 0xFF;
			patOffset += 4;
			if( effect == 0xE ) {
				effect = 0x10 | ( param >> 4 );
				param &= 0xF;
			}
			if( effect == 0 && param > 0 ) {
				effect = 0xE;
			}
			channel.row( key, ins, effect, param );
			switch( effect ) {
				case 0xB: /* Pattern Jump.*/
					if( plCount < 0 ) {
						breakSeqPos = param;
						nextRow = 0;
					}
					break;
				case 0xD: /* Pattern Break.*/
					if( plCount < 0 ) {
						breakSeqPos = seqPos + 1;
						nextRow = ( param >> 4 ) * 10 + ( param & 0xF );
						if( nextRow >= 64 ) {
							nextRow = 0;
						}
					}
					break;
				case 0xF: /* Set Speed.*/
					if( param > 0 ) {
						if( param < 32 ) {
							tick = speed = param;
						} else {
							setTempo( param );
						}
					}
					break;
				case 0x16: /* Pattern Loop.*/
					if( param == 0 ) {
						/* Set loop marker on this channel. */
						channel.plRow = row;
					}
					if( channel.plRow < row ) { /* Marker valid. Begin looping. */
						if( plCount < 0 ) { /* Not already looping, begin. */
							plCount = param;
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
				case 0x1E: /* Pattern Delay.*/
					tick = speed + speed * param;
					break;
			}
		}
		return songEnd;
	}

	var OVERSAMPLE = 2;
	if( samplingRate * OVERSAMPLE < 16000 ) {
		throw "Unsupported sampling rate!";
	}
	var interpolation = false, filtL = 0, filtR = 0, tickLen = 0;
	var seqPos = 0, breakSeqPos = 0, row = 0, nextRow = 0, tick = 0;
	var speed = 0, plCount = 0, plChannel = 0;
	var rampLen = 256;
	while( rampLen * 1024 > samplingRate * OVERSAMPLE ) {
		rampLen /= 2;
	}
	var mixIdx = 0, mixLen = 0;
	var mixBuf = new Int32Array( ( samplingRate * OVERSAMPLE * 5 / 32 ) + ( rampLen * 2 ) );
	var rampBuf = new Int32Array( rampLen * 2 );
	var rampRate = ( 256 / rampLen ) | 0;
	var channels = new Array( module.numChannels );
	this.setSequencePos( 0 );
}

function Channel( module, id, sampleRate ) {
	var FP_SHIFT = 15, FP_ONE = 1 << FP_SHIFT, FP_MASK = FP_ONE - 1;

	var fineTuning = new Int16Array([
		4096, 4067, 4037, 4008, 3979, 3951, 3922, 3894,
		4340, 4308, 4277, 4247, 4216, 4186, 4156, 4126
	]);

	var arpTuning = new Int16Array([
		4096, 4340, 4598, 4871, 5161, 5468, 5793, 6137,
		6502, 6889, 7298, 7732, 8192, 8679, 9195, 9742
	]);

	var sineTable = new Int16Array([
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	]);

	var noteKey = 0, noteEffect = 0, noteParam = 0;
	var noteIns = 0, instrument = 0, assigned = 0;
	var sampleIdx = 0, sampleFra = 0, step = 0;
	var volume = 0, panning = 0, fineTune = 0, ampl = 0;
	var period = 0, portaPeriod = 0, portaSpeed = 0, fxCount = 0;
	var vibratoType = 0, vibratoPhase = 0, vibratoSpeed = 0, vibratoDepth = 0;
	var tremoloType = 0, tremoloPhase = 0, tremoloSpeed = 0, tremoloDepth = 0;
	var tremoloAdd = 0, vibratoAdd = 0, arpeggioAdd = 0;
	var c2Rate = 0, randomSeed = 0;
	var randomSeed = id;
	switch( id & 0x3 ) {
		case 0: case 3: panning =  51; break;
		case 1: case 2: panning = 204; break;
	}
	this.plRow = 0;

	this.resample = function( outBuf, offset, count, interpolate ) {
		if( ampl <= 0 ) return;
		var lAmpl = ampl * panning >> 8;
		var rAmpl = ampl * ( 255 - panning ) >> 8;
		var samIdx = sampleIdx;
		var samFra = sampleFra;
		var stp = step;
		var ins = module.instruments[ instrument ];
		var loopLen = ins.loopLength;
		var loopEp1 = ins.loopStart + loopLen;
		var sampleData = ins.sampleData;
		var outIdx = offset << 1;
		var outEp1 = ( offset + count ) << 1;
		if( interpolate ) {
			while( outIdx < outEp1 ) {
				if( samIdx >= loopEp1 ) {
					if( loopLen <= 1 ) break;
					while( samIdx >= loopEp1 ) samIdx -= loopLen;
				}
				var c = sampleData[ samIdx ];
				var m = sampleData[ samIdx + 1 ] - c;
				var y = ( ( m * samFra ) >> ( FP_SHIFT - 8 ) ) + ( c << 8 );
				outBuf[ outIdx++ ] += ( y * lAmpl ) >> FP_SHIFT;
				outBuf[ outIdx++ ] += ( y * rAmpl ) >> FP_SHIFT;
				samFra += stp;
				samIdx += samFra >> FP_SHIFT;
				samFra &= FP_MASK;
			}
		} else {
			while( outIdx < outEp1 ) {
				if( samIdx >= loopEp1 ) {
					if( loopLen <= 1 ) break;
					while( samIdx >= loopEp1 ) samIdx -= loopLen;
				}
				var y = sampleData[ samIdx ];
				outBuf[ outIdx++ ] += ( y * lAmpl ) >> ( FP_SHIFT - 8 );
				outBuf[ outIdx++ ] += ( y * rAmpl ) >> ( FP_SHIFT - 8 );
				samFra += step;
				samIdx += samFra >> FP_SHIFT;
				samFra &= FP_MASK;
			}
		}
	}

	this.updateSampleIdx = function( count ) {
		sampleFra += step * count;
		sampleIdx += sampleFra >> FP_SHIFT;
		var ins = module.instruments[ instrument ];
		var loopStart = ins.loopStart;
		var loopLength = ins.loopLength;
		var loopOffset = sampleIdx - loopStart;
		if( loopOffset > 0 ) {
			sampleIdx = loopStart;
			if( loopLength > 1 ) sampleIdx += ( loopOffset % loopLength ) | 0;
		}
		sampleFra &= FP_MASK;
	}

	this.row = function( key, ins, effect, param ) {
		noteKey = key;
		noteIns = ins;
		noteEffect = effect;
		noteParam = param;
		vibratoAdd = tremoloAdd = arpeggioAdd = fxCount = 0;
		if( effect != 0x1D ) trigger();
		switch( effect ) {
			case 0x3: /* Tone Portamento.*/
				if( param > 0 ) portaSpeed = param;
				break;
			case 0x4: /* Vibrato.*/
				if( ( param & 0xF0 ) > 0 ) vibratoSpeed = param >> 4;
				if( ( param & 0x0F ) > 0 ) vibratoDepth = param & 0xF;
				vibrato();
				break;
			case 0x6: /* Vibrato + Volume Slide.*/
				vibrato();
				break;
			case 0x7: /* Tremolo.*/
				if( ( param & 0xF0 ) > 0 ) tremoloSpeed = param >> 4;
				if( ( param & 0x0F ) > 0 ) tremoloDepth = param & 0xF;
				tremolo();
				break;
			case 0x8: /* Set Panning. Not for Protracker. */
				if( module.c2Rate == Module.C2_NTSC ) panning = param;
				break;
			case 0x9: /* Set Sample Position.*/
				sampleIdx = param << 8;
				sampleFra = 0;
				break;
			case 0xC: /* Set Volume.*/
				volume = param > 64 ? 64 : param;
				break;
			case 0x11: /* Fine Portamento Up.*/
				period -= param;
				if( period < 0 ) period = 0;
				break;
			case 0x12: /* Fine Portamento Down.*/
				period += param;
				if( period > 65535 ) period = 65535;
				break;
			case 0x14: /* Set Vibrato Waveform.*/
				if( param < 8 ) vibratoType = param;
				break;
			case 0x17: /* Set Tremolo Waveform.*/
				if( param < 8 ) tremoloType = param;
				break;
			case 0x1A: /* Fine Volume Up.*/
				volume += param;
				if( volume > 64 ) volume = 64;
				break;
			case 0x1B: /* Fine Volume Down.*/
				volume -= param;
				if( volume < 0 ) volume = 0;
				break;
			case 0x1C: /* Note Cut.*/
				if( param <= 0 ) volume = 0;
				break;
			case 0x1D: /* Note Delay.*/
				if( param <= 0 ) trigger();
				break;
		}
		updateFrequency();
	}

	this.tick = function() {
		fxCount++;
		switch( noteEffect ) {
			case 0x1: /* Portamento Up.*/
				period -= noteParam;
				if( period < 0 ) period = 0;
				break;
			case 0x2: /* Portamento Down.*/
				period += noteParam;
				if( period > 65535 ) period = 65535;
				break;
			case 0x3: /* Tone Portamento.*/
				tonePortamento();
				break;
			case 0x4: /* Vibrato.*/
				vibratoPhase += vibratoSpeed;
				vibrato();
				break;
			case 0x5: /* Tone Porta + Volume Slide.*/
				tonePortamento();
				volumeSlide( noteParam );
				break;
			case 0x6: /* Vibrato + Volume Slide.*/
				vibratoPhase += vibratoSpeed;
				vibrato();
				volumeSlide( noteParam );
				break;
			case 0x7: /* Tremolo.*/
				tremoloPhase += tremoloSpeed;
				tremolo();
				break;
			case 0xA: /* Volume Slide.*/
				volumeSlide( noteParam );
				break;
			case 0xE: /* Arpeggio.*/
				if( fxCount > 2 ) fxCount = 0;
				if( fxCount == 0 ) arpeggioAdd = 0;
				if( fxCount == 1 ) arpeggioAdd = noteParam >> 4;
				if( fxCount == 2 ) arpeggioAdd = noteParam & 0xF;
				break;
			case 0x19: /* Retrig.*/
				if( fxCount >= noteParam ) {
					fxCount = 0;
					sampleIdx = sampleFra = 0;
				}
				break;
			case 0x1C: /* Note Cut.*/
				if( noteParam == fxCount ) volume = 0;
				break;
			case 0x1D: /* Note Delay.*/
				if( noteParam == fxCount ) trigger();
				break;
		}
		if( noteEffect > 0 ) updateFrequency();
	}

	var updateFrequency = function() {
		var per = period + vibratoAdd;
		if( per < 7 ) {
			 per = 6848;
		}
		var freq = ( module.c2Rate * 428 / per ) | 0;
		freq = ( ( freq * arpTuning[ arpeggioAdd ] ) >> 12 ) & 0x7FFFF;
		step = ( freq * FP_ONE / sampleRate ) | 0;
		var vol = volume + tremoloAdd;
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		vol = ( vol * FP_ONE ) >> 6;
		ampl = ( vol * module.gain ) >> 7;
	}

	var trigger = function() {
		if( noteIns > 0 && noteIns <= module.numInstruments ) {
			assigned = noteIns;
			var assignedIns = module.instruments[ assigned ];
			fineTune = assignedIns.fineTune;
			volume = assignedIns.volume >= 64 ? 64 : assignedIns.volume & 0x3F;
			if( assignedIns.loopLength > 0 && instrument > 0 ) instrument = assigned;
		}
		if( noteEffect == 0x15 ) fineTune = noteParam;
		if( noteKey > 0 ) {
			var key = ( noteKey * fineTuning[ fineTune & 0xF ] ) >> 11;
			portaPeriod = ( key >> 1 ) + ( key & 1 );
			if( noteEffect != 0x3 && noteEffect != 0x5 ) {
				instrument = assigned;
				period = portaPeriod;
				sampleIdx = sampleFra = 0;
				if( vibratoType < 4 ) vibratoPhase = 0;
				if( tremoloType < 4 ) tremoloPhase = 0;
			}
		}
	}
	
	var volumeSlide = function( param ) {
		var vol = volume + ( param >> 4 ) - ( param & 0xF );
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		volume = vol;
	}

	var tonePortamento = function() {
		var src = period;
		var dest = portaPeriod;
		if( src < dest ) {
			src += portaSpeed;
			if( src > dest ) src = dest;
		} else if( src > dest ) {
			src -= portaSpeed;
			if( src < dest ) src = dest;
		}
		period = src;
	}

	var vibrato = function() {
		vibratoAdd = ( waveform( vibratoPhase, vibratoType ) * vibratoDepth ) >> 7;
	}
	
	var tremolo = function() {
		tremoloAdd = ( waveform( tremoloPhase, tremoloType ) * tremoloDepth ) >> 6;
	}

	var waveform = function( phase, type ) {
		var amplitude = 0;
		switch( type & 0x3 ) {
			case 0: /* Sine. */
				amplitude = sineTable[ phase & 0x1F ];
				if( ( phase & 0x20 ) > 0 ) amplitude = -amplitude;
				break;
			case 1: /* Saw Down. */
				amplitude = 255 - ( ( ( phase + 0x20 ) & 0x3F ) << 3 );
				break;
			case 2: /* Square. */
				amplitude = ( phase & 0x20 ) > 0 ? 255 : -255;
				break;
			case 3: /* Random. */
				amplitude = randomSeed - 255;
				randomSeed = ( randomSeed * 65 + 17 ) & 0x1FF;
				break;
		}
		return amplitude;
	}
}

function Instrument() {
	this.instrumentName = "";
	this.volume = 0;
	this.fineTune = 8;
	this.loopStart = 0;
	this.loopLength = 0;
	this.sampleData = new Int8Array( 0 );
}


function Module() {
	this.songName = "Blank";
	this.numChannels = 4;
	this.numInstruments = 1;
	this.numPatterns = 1;
	this.sequenceLength = 1;
	this.restartPos = 0;
	this.c2Rate = 8287;
	this.gain = 64;
	this.patterns = new Int8Array( 64 * 4 * this.numChannels );
	this.sequence = new Int8Array( 1 );
	this.instruments = new Array( this.numInstruments + 1 );
	this.instruments[ 0 ] = this.instruments[ 1 ] = new Instrument();
}

function Module( module ) {
	var ushortbe = function( buf, offset ) {
		return ( ( buf[ offset ] & 0xFF ) << 8 ) | ( buf[ offset + 1 ] & 0xFF );
	}
	
	var ascii = function( buf, offset, len ) {
		var str = "";
		for( var idx = 0; idx < len; idx++ ) {
			var c = buf[ offset + idx ] & 0xFF;
			str += c < 32 ? " " : String.fromCharCode( c );
		}
		return str;
	}

	this.songName = ascii( module, 0, 20 );
	this.sequenceLength = module[ 950 ] & 0x7F;
	this.restartPos = module[ 951 ] & 0x7F;
	if( this.restartPos >= this.sequenceLength ) {
		this.restartPos = 0;
	}
	this.numPatterns = 0;
	this.sequence = new Int8Array( 128 );
	for( var seqIdx = 0; seqIdx < 128; seqIdx++ ) {
		var patIdx = module[ 952 + seqIdx ] & 0x7F;
		this.sequence[ seqIdx ] = patIdx;
		if( patIdx >= this.numPatterns ) {
			this.numPatterns = patIdx + 1;
		}
	}
	switch( ushortbe( module, 1082 ) ) {
		case 0x4b2e: /* M.K. */
		case 0x4b21: /* M!K! */
		case 0x5434: /* FLT4 */
			this.numChannels = 4;
			this.c2Rate = 8287; /* PAL */
			this.gain = 64;
			break;
		case 0x484e: /* xCHN */
			this.numChannels = module[ 1080 ] - 48;
			this.c2Rate = 8363; /* NTSC */
			this.gain = 32;
			break;
		case 0x4348: /* xxCH */
			this.numChannels = ( module[ 1080 ] - 48 ) * 10;
			this.numChannels += module[ 1081 ] - 48;
			this.c2Rate = 8363; /* NTSC */
			this.gain = 32;
			break;
		default:
			throw "MOD Format not recognised!";
	}
	var numNotes = this.numPatterns * 64 * this.numChannels;
	this.patterns = new Int8Array( numNotes * 4 );
	this.patterns.set( module.subarray( 1084, 1084 + numNotes * 4 ) );
	this.numInstruments = 31;
	this.instruments = new Array( this.numInstruments + 1 );
	this.instruments[ 0 ] = new Instrument();
	var modIdx = 1084 + numNotes * 4;
	for( var instIdx = 1; instIdx <= this.numInstruments; instIdx++ ) {
		var inst = new Instrument();
		inst.instrumentName = ascii( module, instIdx * 30 - 10, 22 );
		var sampleLength = ushortbe( module, instIdx * 30 + 12 ) * 2;
		inst.fineTune = module[ instIdx * 30 + 14 ] & 0xF;
		inst.volume = module[ instIdx * 30 + 15 ] & 0x7F;
		if( inst.volume > 64 ) {
			inst.volume = 64;
		}
		var loopStart = ushortbe( module, instIdx * 30 + 16 ) * 2;
		var loopLength = ushortbe( module, instIdx * 30 + 18 ) * 2;
		var sampleData = new Int8Array( sampleLength + 1 );
		if( modIdx + sampleLength > module.length ) {
			sampleLength = module.length - modIdx;
		}
		sampleData.set( module.subarray( modIdx, modIdx + sampleLength ) );
		modIdx += sampleLength;
		if( loopStart + loopLength > sampleLength ) {
			loopLength = sampleLength - loopStart;
		}
		if( loopLength < 4 ) {
			loopStart = sampleLength;
			loopLength = 0;
		}
		sampleData[ loopStart + loopLength ] = sampleData[ loopStart ];
		inst.loopStart = loopStart;
		inst.loopLength = loopLength;
		inst.sampleData = sampleData;
		this.instruments[ instIdx ] = inst;
	}
}


/*
	JavaScript ProTracker Replay (c)2015 mumart@gmail.com
*/
function Micromod( module, samplingRate ) {
	/* Return a String representing the version of the replay. */
	this.getVersion = function() {
		return "20150705 (c)2015 mumart@gmail.com";
	}

	/* Return the sampling rate of playback. */
	this.getSamplingRate = function() {
		return samplingRate;
	}

	/* Set the sampling rate of playback. */
	this.setSamplingRate = function( rate ) {
		/* Use with Module.c2Rate to adjust the tempo of playback. */
		/* To play at half speed, multiply both the samplingRate and Module.c2Rate by 2. */
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
		speed = 6;
		tempo = 125;
		plCount = plChannel = -1;
		for( var idx = 0; idx < module.numChannels; idx++ ) {
			channels[ idx ] = new Channel( module, idx );
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
		if( sequenceRow >= 64 ) {
			sequenceRow = 0;
		}
		while( seqPos < sequencePos || row < sequenceRow ) {
			var tickLen = calculateTickLen( tempo, samplingRate );
			for( var idx = 0; idx < module.numChannels; idx++ ) {
				channels[ idx ].updateSampleIdx( tickLen * 2, samplingRate * 2 );
			}
			if( seqTick() ) { // Song end reached.
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
		// Generate audio. The number of samples produced is returned.
		var tickLen = calculateTickLen( tempo, samplingRate );
		for( var idx = 0, end = ( tickLen + 65 ) * 4; idx < end; idx++ ) {
			// Clear mix buffer.
			mixBuf[ idx ] = 0;
		}
		for( var idx = 0; idx < module.numChannels; idx++ ) {
			// Resample and mix each channel.
			var chan = channels[ idx ];
			chan.resample( mixBuf, 0, ( tickLen + 65 ) * 2, samplingRate * 2, interpolation );
			chan.updateSampleIdx( tickLen * 2, samplingRate * 2 );
		}
		downsample( mixBuf, tickLen + 64 );
		volumeRamp( tickLen );
		// Update the sequencer.
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
		// 2:1 downsampling with simple but effective anti-aliasing.
		// Buf must contain count * 2 + 1 stereo samples.
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
		row = nextRow;
		nextRow = row + 1;
		if( nextRow >= 64 ) {
			breakSeqPos = seqPos + 1;
			nextRow = 0;
		}
		var patOffset = ( module.sequence[ seqPos ] * 64 + row ) * module.numChannels * 4;
		for( var chanIdx = 0; chanIdx < module.numChannels; chanIdx++ ) {
			var channel = channels[ chanIdx ];
			var key = module.patterns[ patOffset ] & 0xFF;
			var ins = module.patterns[ patOffset + 1 ] & 0xFF;
			var effect = module.patterns[ patOffset + 2 ] & 0xFF;
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
						if( breakSeqPos < 0 ) {
							breakSeqPos = seqPos + 1;
						}
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
							tempo = param;
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

	var interpolation = false;
	var rampBuf = new Float32Array( 64 * 2 );
	var mixBuf = new Float32Array( ( calculateTickLen( 32, 128000 ) + 65 ) * 4 );
	var mixIdx = 0, mixLen = 0;
	var seqPos = 0, breakSeqPos = 0, row = 0, nextRow = 0, tick = 0;
	var speed = 0, tempo = 0, plCount = 0, plChannel = 0;
	var channels = new Array( module.numChannels );
	this.setSamplingRate( samplingRate );
	this.setSequencePos( 0 );
}

function Channel( module, id ) {
	var fineTuning = new Int16Array([
		4096, 4067, 4037, 4008, 3979, 3951, 3922, 3894,
		4340, 4308, 4277, 4247, 4216, 4186, 4156, 4126
	]);

	var sineTable = new Int16Array([
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	]);

	var noteKey = 0, noteEffect = 0, noteParam = 0;
	var noteIns = 0, instrument = 0, assigned = 0;
	var sampleOffset = 0, sampleIdx = 0, freq = 0;
	var volume = 0, panning = 0, fineTune = 0, ampl = 0;
	var period = 0, portaPeriod = 0, portaSpeed = 0, fxCount = 0;
	var vibratoType = 0, vibratoPhase = 0, vibratoSpeed = 0, vibratoDepth = 0;
	var tremoloType = 0, tremoloPhase = 0, tremoloSpeed = 0, tremoloDepth = 0;
	var tremoloAdd = 0, vibratoAdd = 0, arpeggioAdd = 0;
	var randomSeed = ( id + 1 ) * 0xABCDEF;
	switch( id & 0x3 ) {
		case 0: case 3: panning =  51; break;
		case 1: case 2: panning = 204; break;
	}
	this.plRow = 0;

	this.resample = function( outBuf, offset, count, sampleRate, interpolate ) {
		if( ampl <= 0 ) return;
		var lGain = ampl * panning / 32768;
		var rGain = ampl * ( 255 - panning ) / 32768;
		var samIdx = sampleIdx;
		var step = freq / sampleRate;
		var ins = module.instruments[ instrument ];
		var loopLen = ins.loopLength;
		var loopEnd = ins.loopStart + loopLen;
		var sampleData = ins.sampleData;
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
		var ins = module.instruments[ instrument ];
		if( sampleIdx > ins.loopStart ) {
			if( ins.loopLength > 1 ) {
				sampleIdx = ins.loopStart + ( sampleIdx - ins.loopStart ) % ins.loopLength;
			} else {
				sampleIdx = ins.loopStart;
			}
		}
	}

	this.row = function( key, ins, effect, param ) {
		noteKey = key;
		noteIns = ins;
		noteEffect = effect;
		noteParam = param;
		vibratoAdd = tremoloAdd = arpeggioAdd = fxCount = 0;
		if( !( effect == 0x1D && param > 0 ) ) {
			/* Not note delay. */
			trigger();
		}
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
			case 0x8: /* Set Panning. Not for 4-channel ProTracker. */
				if( module.numChannels != 4 ) {
					panning = ( param < 128 ) ? ( param << 1 ) : 255;
				}
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
		per = per * module.keyToPeriod[ arpeggioAdd ] / module.keyToPeriod[ 0 ];
		if( per < 7 ) {
			 per = 6848;
		}
		freq = module.c2Rate * 428 / per;
		var vol = volume + tremoloAdd;
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		ampl = vol * module.gain / 8192;
	}

	var trigger = function() {
		if( noteIns > 0 && noteIns <= module.numInstruments ) {
			assigned = noteIns;
			var assignedIns = module.instruments[ assigned ];
			sampleOffset = 0;
			fineTune = assignedIns.fineTune;
			volume = assignedIns.volume >= 64 ? 64 : assignedIns.volume & 0x3F;
			if( assignedIns.loopLength > 0 && instrument > 0 ) instrument = assigned;
		}
		if( noteEffect == 0x09 ) {
			sampleOffset = ( noteParam & 0xFF ) << 8;
		} else if( noteEffect == 0x15 ) {
			fineTune = noteParam;
		}
		if( noteKey > 0 && noteKey <= 72 ) {
			var per = ( module.keyToPeriod[ noteKey ] * fineTuning[ fineTune & 0xF ] ) >> 11;
			portaPeriod = ( per >> 1 ) + ( per & 1 );
			if( noteEffect != 0x3 && noteEffect != 0x5 ) {
				instrument = assigned;
				period = portaPeriod;
				sampleIdx = sampleOffset;
				sampleFra = 0;
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
				amplitude = ( randomSeed >> 20 ) - 255;
				randomSeed = ( randomSeed * 65 + 17 ) & 0x1FFFFFFF;
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

function Module( module ) {
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
	this.keyToPeriod = new Int16Array([ 1814,
	/*	 C-0   C#0   D-0   D#0   E-0   F-0   F#0   G-0   G#0   A-0  A#0  B-0 */
		1712, 1616, 1524, 1440, 1356, 1280, 1208, 1140, 1076, 1016, 960, 907,
		 856,  808,  762,  720,  678,  640,  604,  570,  538,  508, 480, 453,
		 428,  404,  381,  360,  339,  320,  302,  285,  269,  254, 240, 226,
		 214,  202,  190,  180,  170,  160,  151,  143,  135,  127, 120, 113,
		 107,  101,   95,   90,   85,   80,   75,   71,   67,   63,  60,  56,
		  53,   50,   47,   45,   42,   40,   37,   35,   33,   31,  30,  28
	]);
	if( module != undefined ) {
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
		for( var patIdx = 0; patIdx < this.patterns.length; patIdx += 4 ) {
			var period = ( module[ 1084 + patIdx ] & 0xF ) << 8;
			period = period | ( module[ 1084 + patIdx + 1 ] & 0xFF );
			if( period < 28 ) {
				this.patterns[ patIdx ] = 0;
			} else {
				/* Convert period to key. */
				var key = 0, oct = 0;
				while( period < 907 ) {
					period *= 2;
					oct++;
				}
				while( key < 12 ) {
					var d1 = this.keyToPeriod[ key ] - period;
					var d2 = period - this.keyToPeriod[ key + 1 ];
					if( d2 >= 0 ) {
						if( d2 < d1 ) key++;
						break;
					}
					key++;
				}
				this.patterns[ patIdx ] = oct * 12 + key;
			}
			var ins = ( module[ 1084 + patIdx + 2 ] & 0xF0 ) >> 4;
			this.patterns[ patIdx + 1 ] = ins | ( module[ 1084 + patIdx ] & 0x10 );
			this.patterns[ patIdx + 2 ] = module[ 1084 + patIdx + 2 ] & 0xF;
			this.patterns[ patIdx + 3 ] = module[ 1084 + patIdx + 3 ];
		}
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
			inst.loopStart = ushortbe( module, instIdx * 30 + 16 ) * 2;
			inst.loopLength = ushortbe( module, instIdx * 30 + 18 ) * 2;
			if( inst.loopStart + inst.loopLength > sampleLength ) {
				inst.loopLength = sampleLength - inst.loopStart;
			}
			if( inst.loopLength < 4 ) {
				inst.loopStart = sampleLength;
				inst.loopLength = 0;
			}
			inst.sampleData = new Int8Array( sampleLength + 1 );
			if( modIdx + sampleLength > module.length ) {
				sampleLength = module.length - modIdx;
			}
			inst.sampleData.set( module.subarray( modIdx, modIdx + sampleLength ) );
			inst.sampleData[ inst.loopStart + inst.loopLength ] = inst.sampleData[ inst.loopStart ];
			modIdx += sampleLength;
			this.instruments[ instIdx ] = inst;
		}
	}
}

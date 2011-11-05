
package ibxm;

public class Channel {
	public static final int NEAREST = 0, LINEAR = 1, SINC = 2;

	private static final int[] periodTable = {
		// Periods for keys -11 to 1 with 8 finetune values.
		54784, 54390, 53999, 53610, 53224, 52841, 52461, 52084, 
		51709, 51337, 50968, 50601, 50237, 49876, 49517, 49161, 
		48807, 48456, 48107, 47761, 47418, 47076, 46738, 46401, 
		46068, 45736, 45407, 45081, 44756, 44434, 44115, 43797, 
		43482, 43169, 42859, 42550, 42244, 41940, 41639, 41339, 
		41042, 40746, 40453, 40162, 39873, 39586, 39302, 39019, 
		38738, 38459, 38183, 37908, 37635, 37365, 37096, 36829, 
		36564, 36301, 36040, 35780, 35523, 35267, 35014, 34762, 
		34512, 34263, 34017, 33772, 33529, 33288, 33049, 32811, 
		32575, 32340, 32108, 31877, 31647, 31420, 31194, 30969, 
		30746, 30525, 30306, 30088, 29871, 29656, 29443, 29231, 
		29021, 28812, 28605, 28399, 28195, 27992, 27790, 27590, 
		27392, 27195, 26999, 26805, 26612, 26421, 26231, 26042
	};

	private static final int[] freqTable = {
		// Frequency for keys 109 to 121 with 8 fractional values.
		267616, 269555, 271509, 273476, 275458, 277454, 279464, 281489,
		283529, 285584, 287653, 289738, 291837, 293952, 296082, 298228,
		300389, 302566, 304758, 306966, 309191, 311431, 313688, 315961,
		318251, 320557, 322880, 325220, 327576, 329950, 332341, 334749,
		337175, 339618, 342079, 344558, 347055, 349570, 352103, 354655,
		357225, 359813, 362420, 365047, 367692, 370356, 373040, 375743,
		378466, 381209, 383971, 386754, 389556, 392379, 395222, 398086,
		400971, 403877, 406803, 409751, 412720, 415711, 418723, 421758,
		424814, 427892, 430993, 434116, 437262, 440430, 443622, 446837,
		450075, 453336, 456621, 459930, 463263, 466620, 470001, 473407,
		476838, 480293, 483773, 487279, 490810, 494367, 497949, 501557,
		505192, 508853, 512540, 516254, 519995, 523763, 527558, 531381,
		535232, 539111, 543017, 546952, 550915, 554908, 558929, 562979
	};

	private static final short[] arpTuning = {
		4096, 4340, 4598, 4871, 5161, 5468, 5793, 6137,
		6502, 6889, 7298, 7732, 8192, 8679, 9195, 9742
	};

	private static final short[] sineTable = {
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	};

	private Module module;
	private GlobalVol globalVol;
	private Instrument instrument;
	private Sample sample;
	private boolean keyOn;
	private int noteKey, noteIns, noteVol, noteEffect, noteParam;
	private int sampleIdx, sampleFra, step, ampl, pann;
	private int volume, panning, fadeOutVol, volEnvTick, panEnvTick;
	private int period, portaPeriod, retrigCount, fxCount, autoVibratoCount;
	private int portaUpParam, portaDownParam, tonePortaParam, offsetParam;
	private int finePortaUpParam, finePortaDownParam, extraFinePortaParam;
	private int arpeggioParam, vslideParam, globalVslideParam, panningSlideParam;
	private int fineVslideUpParam, fineVslideDownParam;
	private int retrigVolume, retrigTicks, tremorOnTicks, tremorOffTicks;
	private int vibratoType, vibratoPhase, vibratoSpeed, vibratoDepth;
	private int tremoloType, tremoloPhase, tremoloSpeed, tremoloDepth;
	private int tremoloAdd, vibratoAdd, arpeggioAdd;
	private int id, sampleRate, randomSeed;
	public int plRow;
	
	public Channel( Module module, int id, int sampleRate, GlobalVol globalVol ) {
		this.module = module;
		this.id = id;
		this.sampleRate = sampleRate;
		this.globalVol = globalVol;
		panning = module.defaultPanning[ id ];
		instrument = new Instrument();
		sample = instrument.samples[ 0 ];
		randomSeed = ( id + 1 ) * 0xABCDEF;
	}

	public void resample( int[] outBuf, int offset, int length, int interpolation ) {
		if( ampl <= 0 ) return;
		int lAmpl = ampl * ( 255 - pann ) >> 8;
		int rAmpl = ampl * pann >> 8;
		switch( interpolation ) {
			case NEAREST:
				sample.resampleNearest( sampleIdx, sampleFra, step, lAmpl, rAmpl, outBuf, offset, length );
				break;
			case LINEAR: default:
				sample.resampleLinear( sampleIdx, sampleFra, step, lAmpl, rAmpl, outBuf, offset, length );
				break;
			case SINC:
				sample.resampleSinc( sampleIdx, sampleFra, step, lAmpl, rAmpl, outBuf, offset, length );
				break;
		}
	}

	public void updateSampleIdx( int length ) {
		sampleFra += step * length;
		sampleIdx = sample.normaliseSampleIdx( sampleIdx + ( sampleFra >> Sample.FP_SHIFT ) );
		sampleFra &= Sample.FP_MASK;
	}

	public void row( Note note ) {
		noteKey = note.key;
		noteIns = note.instrument;
		noteVol = note.volume;
		noteEffect = note.effect;
		noteParam = note.param;
		retrigCount++;
		vibratoAdd = tremoloAdd = arpeggioAdd = fxCount = 0;
		if( noteEffect != 0x7D && noteEffect != 0xFD ) trigger();
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
				panning = noteParam & 0xFF;
				break;
			case 0x09: case 0x8F: /* Set Sample Offset. */
				if( noteParam > 0 ) offsetParam = noteParam;
				sampleIdx = offsetParam << 8;
				sampleFra = 0;
				break;
			case 0x0A: case 0x84: /* Vol Slide. */
				if( noteParam > 0 ) vslideParam = noteParam;
				volumeSlide();
				break;
			case 0x0C: /* Set Volume. */
				volume = noteParam >= 64 ? 64 : noteParam & 0x3F;
				break;
			case 0x10: case 0x96: /* Set Global Volume. */
				globalVol.volume = noteParam >= 64 ? 64 : noteParam & 0x3F;
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
			case 0x7D: case 0xFD: /* Note Delay. */
				if( noteParam <= 0 ) trigger();
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
	
	public void tick() {
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
				globalVol.volume += ( globalVslideParam >> 4 ) - ( globalVslideParam & 0xF );
				if( globalVol.volume < 0 ) globalVol.volume = 0;
				if( globalVol.volume > 64 ) globalVol.volume = 64;
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
					sampleIdx = sampleFra = 0;
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

	private void updateEnvelopes() {
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

	private void autoVibrato() {
		int depth = instrument.vibratoDepth & 0x7F;
		if( depth > 0 ) {
			int sweep = instrument.vibratoSweep & 0x7F;
			int rate = instrument.vibratoRate & 0x7F;
			int type = instrument.vibratoType;
			if( autoVibratoCount < sweep ) depth = depth * autoVibratoCount / sweep;
			vibratoAdd += waveform( autoVibratoCount * rate >> 2, type + 4 ) * depth >> 8;
			autoVibratoCount++;
		}
	}

	private void volumeSlide() {
		int up = vslideParam >> 4;
		int down = vslideParam & 0xF;
		if( down == 0xF && up > 0 ) { /* Fine slide up.*/
			if( fxCount == 0 ) volume += up;
		} else if( up == 0xF && down > 0 ) { /* Fine slide down.*/
			if( fxCount == 0 ) volume -= down;
		} else if( fxCount > 0 || module.fastVolSlides ) /* Normal.*/
			volume += up - down;
		if( volume > 64 ) volume = 64;
		if( volume < 0 ) volume = 0;
	}

	private void portamentoUp( int param ) {
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

	private void portamentoDown( int param ) {
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

	private void tonePortamento() {
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

	private void vibrato( boolean fine ) {
		vibratoAdd = waveform( vibratoPhase, vibratoType & 0x3 ) * vibratoDepth >> ( fine ? 7 : 5 );
	}

	private void tremolo() {
		tremoloAdd = waveform( tremoloPhase, tremoloType & 0x3 ) * tremoloDepth >> 6;
	}

	private int waveform( int phase, int type ) {
		int amplitude = 0;
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

	private void tremor() {
		if( retrigCount >= tremorOnTicks ) tremoloAdd = -64;
		if( retrigCount >= ( tremorOnTicks + tremorOffTicks ) )
			tremoloAdd = retrigCount = 0;
	}

	private void retrigVolSlide() {
		if( retrigCount >= retrigTicks ) {
			retrigCount = sampleIdx = sampleFra = 0;
			switch( retrigVolume ) {
				case 0x1: volume -=  1; break;
				case 0x2: volume -=  2; break;
				case 0x3: volume -=  4; break;
				case 0x4: volume -=  8; break;
				case 0x5: volume -= 16; break;
				case 0x6: volume -= volume / 3; break;
				case 0x7: volume >>= 1; break;
				case 0x8: /* ? */ break;
				case 0x9: volume +=  1; break;
				case 0xA: volume +=  2; break;
				case 0xB: volume +=  4; break;
				case 0xC: volume +=  8; break;
				case 0xD: volume += 16; break;
				case 0xE: volume += volume >> 1; break;
				case 0xF: volume <<= 1; break;
			}
			if( volume <  0 ) volume = 0;
			if( volume > 64 ) volume = 64;
		}
	}

	private void calculateFrequency() {
		if( module.linearPeriods ) {
			int per = period + vibratoAdd - ( arpeggioAdd << 6 );
			if( per < 28 || per > 7680 ) per = 7680;
			int tone = 7680 - per;
			int i = ( tone >> 3 ) % 96;
			int c = freqTable[ i ];
			int m = freqTable[ i + 1 ] - c;
			int x = tone & 0x7;
			int y = ( ( m * x ) >> 3 ) + c;
			int freq = y >> ( 9 - tone / 768 );
			if( freq < 65536 ) step = ( freq << Sample.FP_SHIFT ) / sampleRate;
			else step = ( freq << ( Sample.FP_SHIFT - 3 ) ) / ( sampleRate >> 3 );
		} else {
			int per = period + vibratoAdd;
			if( per < 28 ) per = periodTable[ 0 ];
			int freq = module.c2Rate * 1712 / per;
			freq = ( freq * arpTuning[ arpeggioAdd ] >> 12 ) & 0x7FFFF;
			if( freq < 65536 ) step = ( freq << Sample.FP_SHIFT ) / sampleRate;
			else step = ( freq << ( Sample.FP_SHIFT - 3 ) ) / ( sampleRate >> 3 );
		}
	}

	private void calculateAmplitude() {
		int envVol = keyOn ? 64 : 0;
		if( instrument.volumeEnvelope.enabled )
			envVol = instrument.volumeEnvelope.calculateAmpl( volEnvTick );
		int vol = volume + tremoloAdd;
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		vol = vol * module.gain * Sample.FP_ONE >> 13;
		vol = vol * fadeOutVol >> 15;
		ampl = vol * globalVol.volume * envVol >> 12;
		int envPan = 32;
		if( instrument.panningEnvelope.enabled )
			envPan = instrument.panningEnvelope.calculateAmpl( panEnvTick );
		int panRange = ( panning < 128 ) ? panning : ( 255 - panning );
		pann = panning + ( panRange * ( envPan - 32 ) >> 5 );
	}
	
	private void trigger() {
		if( noteIns > 0 && noteIns <= module.numInstruments ) {
			instrument = module.instruments[ noteIns ];
			Sample sam = instrument.samples[ instrument.keyToSample[ noteKey < 97 ? noteKey : 0 ] ];
			volume = sam.volume >= 64 ? 64 : sam.volume & 0x3F;
			if( sam.panning >= 0 ) panning = sam.panning & 0xFF;
			if( period > 0 && sam.looped() ) sample = sam; /* Amiga trigger.*/
			volEnvTick = panEnvTick = 0;
			fadeOutVol = 32768;
			keyOn = true;
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
				boolean isPorta = ( noteVol & 0xF0 ) == 0xF0 ||
					noteEffect == 0x03 || noteEffect == 0x05 ||
					noteEffect == 0x87 || noteEffect == 0x8C;
				if( !isPorta ) sample = instrument.samples[ instrument.keyToSample[ noteKey ] ];
				byte fineTune = ( byte ) sample.fineTune;
				if( noteEffect == 0x75 || noteEffect == 0xF2 ) /* Set FineTune. */
					fineTune = ( byte ) ( ( noteParam & 0xF ) << 4 );
				int key = noteKey + sample.relNote;
				if( key < 1 ) key = 1;
				if( key > 120 ) key = 120;
				if( module.linearPeriods ) {
					portaPeriod = 7680 - ( ( key - 1 ) << 6 ) - ( fineTune >> 1 );
				} else {
					int tone = 768 + ( ( key - 1 ) << 6 ) + ( fineTune >> 1 );
					int i = ( tone >> 3 ) % 96;
					int c = periodTable[ i ];
					int m = periodTable[ i + 1 ] - c;
					int x = tone & 0x7;
					int y = ( ( m * x ) >> 3 ) + c;
					portaPeriod = y >> ( tone / 768 );
					portaPeriod = module.c2Rate * portaPeriod / sample.c2Rate;
				}
				if( !isPorta ) {
					period = portaPeriod;
					sampleIdx = sampleFra = 0;
					if( vibratoType < 4 ) vibratoPhase = 0;
					if( tremoloType < 4 ) tremoloPhase = 0;
					retrigCount = autoVibratoCount = 0;
				}
			}
		}
	}
}


package micromod;

public class Channel {
	private static final short[] sineTable = {
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	};

	private Module module;
	private int noteKey, noteEffect, noteParam;
	private int noteIns, instrument, assigned;
	private int sampleOffset, sampleIdx, sampleFra, freq;
	private int volume, panning, fineTune;
	private int period, portaPeriod, portaSpeed, fxCount;
	private int vibratoType, vibratoPhase, vibratoSpeed, vibratoDepth;
	private int tremoloType, tremoloPhase, tremoloSpeed, tremoloDepth;
	private int tremoloAdd, vibratoAdd, arpeggioAdd;
	private int id, randomSeed;
	public int plRow;

	public Channel( Module module, int id ) {
		this.module = module;
		this.id = id;
		switch( id & 0x3 ) {
			case 0: case 3: panning =  51; break;
			case 1: case 2: panning = 204; break;
		}
		randomSeed = ( id + 1 ) * 0xABCDEF;
	}
	
	public void resample( int[] mixBuf, int offset, int count, int sampleRate, boolean interpolation ) {
		if( instrument > 0 ) {
			int ampl = ( volume * module.getGain() * Instrument.FP_ONE ) >> 13;
			if( ampl > 0 ) {
				int leftGain = ( ampl * panning ) >> 8;
				int rightGain = ( ampl * ( 255 - panning ) ) >> 8;
				int step = ( freq << ( Instrument.FP_SHIFT - 3 ) ) / ( sampleRate >> 3 );
				module.getInstrument( instrument ).getAudio( sampleIdx, sampleFra, step,
					leftGain, rightGain, mixBuf, offset, count, interpolation );
			}
		}
	}

	public void updateSampleIdx( int length, int sampleRate ) {
		if( instrument > 0 ) {
			int step = ( freq << ( Instrument.FP_SHIFT - 3 ) ) / ( sampleRate >> 3 );
			sampleFra += step * length;
			sampleIdx += sampleFra >> Instrument.FP_SHIFT;
			sampleIdx = module.getInstrument( instrument ).normalizeSampleIdx( sampleIdx );
			sampleFra &= Instrument.FP_MASK;
		}
	}

	public void row( int key, int ins, int effect, int param ) {
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
				if( module.getNumChannels() != 4 ) {
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

	public void tick() {
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

	private void updateFrequency() {
		int period = Note.transpose( this.period + vibratoAdd, arpeggioAdd );
		if( period < 7 ) period = 6848;
		freq = module.getC2Rate() * 428 / period;
		int volume = this.volume + tremoloAdd;
		if( volume > 64 ) volume = 64;
		if( volume < 0 ) volume = 0;
	}

	private void trigger() {
		if( noteIns > 0 && noteIns < 32 ) {
			assigned = noteIns;
			Instrument assignedIns = module.getInstrument( assigned );
			sampleOffset = 0;
			fineTune = assignedIns.getFineTune();
			volume = assignedIns.getVolume() & 0x7F;
			if( volume > 64 ) volume = 64;
			if( assignedIns.getLoopLength() > 0 && instrument > 0 ) instrument = assigned;
		}
		if( noteEffect == 0x09 ) {
			sampleOffset = ( noteParam & 0xFF ) << 8;
		} else if( noteEffect == 0x15 ) {
			fineTune = ( noteParam & 0x7 ) - ( noteParam & 0x8 );
		}
		if( noteKey > 0 ) {
			portaPeriod = Note.keyToPeriod( noteKey, fineTune );
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

	private void volumeSlide( int param ) {
		int vol = volume + ( param >> 4 ) - ( param & 0xF );
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		volume = vol;
	}

	private void tonePortamento() {
		int source = period;
		int dest = portaPeriod;
		if( source < dest ) {
			source += portaSpeed;
			if( source > dest ) source = dest;
		} else if( source > dest ) {
			source -= portaSpeed;
			if( source < dest ) source = dest;
		}
		period = source;
	}

	private void vibrato() {
		vibratoAdd = waveform( vibratoPhase, vibratoType ) * vibratoDepth >> 7;
	}
	
	private void tremolo() {
		tremoloAdd = waveform( tremoloPhase, tremoloType ) * tremoloDepth >> 6;
	}

	private int waveform( int phase, int type ) {
		int amplitude = 0;
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

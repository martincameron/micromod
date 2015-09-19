package micromod;

public class Channel extends AbstractChannel {
	private static final short[] sineTable = {
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	};

	private Module module;
	private int noteKey, noteEffect, noteParam;
	private int noteIns, instrument, assigned;
	private int sampleOffset, sampleIdx, sampleFra;
	private int volume, panning, fineTune, freq, ampl;
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

	@Override
	public void resample( int[] mixBuf, int offset, int count, int sampleRate, ChannelInterpolation interpolation ) {
		if( instrument > 0 && ampl > 0 ) {
			int leftGain = ( ampl * panning ) >> 8;
			int rightGain = ( ampl * ( 255 - panning ) ) >> 8;
			int step = ( freq << ( Instrument.FP_SHIFT - 3 ) ) / ( sampleRate >> 3 );
			module.getInstrument( instrument ).getAudio( sampleIdx, sampleFra, step,
				leftGain, rightGain, mixBuf, offset, count, interpolation == ChannelInterpolation.LINEAR );
		}
	}

	@Override
	public void updateSampleIdx( int length, int sampleRate ) {
		if( instrument > 0 ) {
			int step = ( freq << ( Instrument.FP_SHIFT - 3 ) ) / ( sampleRate >> 3 );
			sampleFra += step * length;
			sampleIdx += sampleFra >> Instrument.FP_SHIFT;
			sampleIdx = module.getInstrument( instrument ).normalizeSampleIdx( sampleIdx );
			sampleFra &= Instrument.FP_MASK;
		}
	}

	@Override
	public void row( Note note ) {
		noteKey = note.key;
		noteIns = note.instrument;
		noteEffect = note.effect;
		noteParam = note.parameter;
		vibratoAdd = tremoloAdd = arpeggioAdd = fxCount = 0;
		if( !( noteEffect == 0x1D && noteParam > 0 ) ) {
			/* Not note delay. */
			trigger();
		}
		switch( noteEffect ) {
			case 0x3: /* Tone Portamento.*/
				if( noteParam > 0 ) portaSpeed = noteParam;
				break;
			case 0x4: /* Vibrato.*/
				if( ( noteParam & 0xF0 ) > 0 ) vibratoSpeed = noteParam >> 4;
				if( ( noteParam & 0x0F ) > 0 ) vibratoDepth = noteParam & 0xF;
				vibrato();
				break;
			case 0x6: /* Vibrato + Volume Slide.*/
				vibrato();
				break;
			case 0x7: /* Tremolo.*/
				if( ( noteParam & 0xF0 ) > 0 ) tremoloSpeed = noteParam >> 4;
				if( ( noteParam & 0x0F ) > 0 ) tremoloDepth = noteParam & 0xF;
				tremolo();
				break;
			case 0x8: /* Set Panning. Not for 4-channel ProTracker. */
				if( module.getNumChannels() != 4 ) {
					panning = ( noteParam < 128 ) ? ( noteParam << 1 ) : 255;
				}
				break;
			case 0xC: /* Set Volume.*/
				volume = noteParam > 64 ? 64 : noteParam;
				break;
			case 0x11: /* Fine Portamento Up.*/
				period -= noteParam;
				if( period < 0 ) period = 0;
				break;
			case 0x12: /* Fine Portamento Down.*/
				period += noteParam;
				if( period > 65535 ) period = 65535;
				break;
			case 0x14: /* Set Vibrato Waveform.*/
				if( noteParam < 8 ) vibratoType = noteParam;
				break;
			case 0x17: /* Set Tremolo Waveform.*/
				if( noteParam < 8 ) tremoloType = noteParam;
				break;
			case 0x1A: /* Fine Volume Up.*/
				volume += noteParam;
				if( volume > 64 ) volume = 64;
				break;
			case 0x1B: /* Fine Volume Down.*/
				volume -= noteParam;
				if( volume < 0 ) volume = 0;
				break;
			case 0x1C: /* Note Cut.*/
				if( noteParam <= 0 ) volume = 0;
				break;
		}
		updateFrequency();
	}

	@Override
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
		int per = Note.transpose( this.period + vibratoAdd, arpeggioAdd );
		if( per < 7 ) per = 6848;
		freq = module.getC2Rate() * 428 / per;
		int vol = this.volume + tremoloAdd;
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		ampl = ( vol * module.getGain() * Instrument.FP_ONE ) >> 13;
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

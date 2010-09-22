
package mumart.micromod.mod;

public class Channel {
	private static final int
		FP_SHIFT = 15,
		FP_ONE = 1 << FP_SHIFT,
		FP_MASK = FP_ONE - 1;

	private static final short[] fine_tuning = {
		4096, 4067, 4037, 4008, 3979, 3951, 3922, 3894,
		4340, 4308, 4277, 4247, 4216, 4186, 4156, 4126
	};

	private static final short[] arp_tuning = {
		4096, 4340, 4598, 4871, 5161, 5468, 5793, 6137,
		6502, 6889, 7298, 7732, 8192, 8679, 9195, 9742
	};

	private static final short[] sine_table = {
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	};

	private Module module;
	private int note_key, note_effect, note_param;
	private int note_ins, instrument, assigned;
	private int sample_idx, sample_fra, step;
	private int volume, panning, fine_tune, ampl;
	private int period, porta_period, porta_speed, fx_count;
	private int vibrato_type, vibrato_phase, vibrato_speed, vibrato_depth;
	private int tremolo_type, tremolo_phase, tremolo_speed, tremolo_depth;
	private int tremolo_add, vibrato_add, arpeggio_add;
	private int id, c2_rate, sample_rate, gain, random_seed;
	public int pl_row;
	
	public Channel( Module module, int id, int sample_rate ) {
		this.module = module;
		this.id = random_seed = id;
		this.sample_rate = sample_rate;
		switch( id & 0x3 ) {
			case 0: case 3: panning =  51; break;
			case 1: case 2: panning = 204; break;
		}
	}
	
	public void resample( int[] out_buf, int offset, int length, boolean interpolate ) {
		if( ampl <= 0 ) return;
		int l_ampl = ampl * panning >> 8;
		int r_ampl = ampl * ( 255 - panning ) >> 8;
		int sam_idx = sample_idx;
		int sam_fra = sample_fra;
		int step = this.step;
		Instrument ins = module.instruments[ instrument ];
		int loop_len = ins.loop_length;
		int loop_ep1 = ins.loop_start + loop_len;
		byte[] sample_data = ins.sample_data;
		int out_idx = offset << 1;
		int out_ep1 = offset + length << 1;
		if( interpolate ) {
			while( out_idx < out_ep1 ) {
				if( sam_idx >= loop_ep1 ) {
					if( loop_len <= 1 ) break;
					while( sam_idx >= loop_ep1 ) sam_idx -= loop_len;
				}
				int c = sample_data[ sam_idx ];
				int m = sample_data[ sam_idx + 1 ] - c;
				int y = ( m * sam_fra >> FP_SHIFT - 8 ) + ( c << 8 );
				out_buf[ out_idx++ ] += y * l_ampl >> FP_SHIFT;
				out_buf[ out_idx++ ] += y * r_ampl >> FP_SHIFT;
				sam_fra += step;
				sam_idx += sam_fra >> FP_SHIFT;
				sam_fra &= FP_MASK;
			}
		} else {
			while( out_idx < out_ep1 ) {
				if( sam_idx >= loop_ep1 ) {
					if( loop_len <= 1 ) break;
					while( sam_idx >= loop_ep1 ) sam_idx -= loop_len;
				}
				int y = sample_data[ sam_idx ];
				out_buf[ out_idx++ ] += y * l_ampl >> FP_SHIFT - 8;
				out_buf[ out_idx++ ] += y * r_ampl >> FP_SHIFT - 8;
				sam_fra += step;
				sam_idx += sam_fra >> FP_SHIFT;
				sam_fra &= FP_MASK;
			}
		}
	}

	public void update_sample_idx( int length ) {
		sample_fra += step * length;
		sample_idx += sample_fra >> FP_SHIFT;
		Instrument ins = module.instruments[ instrument ];
		int loop_start = ins.loop_start;
		int loop_length = ins.loop_length;
		int loop_offset = sample_idx - loop_start;
		if( loop_offset > 0 ) {
			sample_idx = loop_start;
			if( loop_length > 1 ) sample_idx += loop_offset % loop_length;
		}
		sample_fra &= FP_MASK;
	}

	public void row( int key, int ins, int effect, int param ) {
		note_key = key;
		note_ins = ins;
		note_effect = effect;
		note_param = param;
		vibrato_add = tremolo_add = arpeggio_add = fx_count = 0;
		if( effect != 0x1D ) trigger();
		switch( effect ) {
			case 0x3: /* Tone Portamento.*/
				if( param > 0 ) porta_speed = param;
				break;
			case 0x4: /* Vibrato.*/
				if( ( param & 0xF0 ) > 0 ) vibrato_speed = param >> 4;
				if( ( param & 0x0F ) > 0 ) vibrato_depth = param & 0xF;
				vibrato();
				break;
			case 0x6: /* Vibrato + Volume Slide.*/
				vibrato();
				break;
			case 0x7: /* Tremolo.*/
				if( ( param & 0xF0 ) > 0 ) tremolo_speed = param >> 4;
				if( ( param & 0x0F ) > 0 ) tremolo_depth = param & 0xF;
				tremolo();
				break;
			case 0x8: /* Set Panning. Not for Protracker. */
				if( module.c2_rate == Module.C2_NTSC ) panning = param;
				break;
			case 0x9: /* Set Sample Position.*/
				sample_idx = param << 8;
				sample_fra = 0;
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
				if( param < 8 ) vibrato_type = param;
				break;
			case 0x15: /* Set Finetune.*/
				fine_tune = param;
				break;
			case 0x17: /* Set Tremolo Waveform.*/
				if( param < 8 ) tremolo_type = param;
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
		update_frequency();
	}

	public void tick() {
		fx_count++;
		switch( note_effect ) {
			case 0x1: /* Portamento Up.*/
				period -= note_param;
				if( period < 0 ) period = 0;
				break;
			case 0x2: /* Portamento Down.*/
				period += note_param;
				if( period > 65535 ) period = 65535;
				break;
			case 0x3: /* Tone Portamento.*/
				tone_portamento();
				break;
			case 0x4: /* Vibrato.*/
				vibrato_phase += vibrato_speed;
				vibrato();
				break;
			case 0x5: /* Tone Porta + Volume Slide.*/
				tone_portamento();
				volume_slide( note_param );
				break;
			case 0x6: /* Vibrato + Volume Slide.*/
				vibrato_phase += vibrato_speed;
				vibrato();
				volume_slide( note_param );
				break;
			case 0x7: /* Tremolo.*/
				tremolo_phase += tremolo_speed;
				tremolo();
				break;
			case 0xA: /* Volume Slide.*/
				volume_slide( note_param );
				break;
			case 0xE: /* Arpeggio.*/
				if( fx_count > 2 ) fx_count = 0;
				if( fx_count == 0 ) arpeggio_add = 0;
				if( fx_count == 1 ) arpeggio_add = note_param >> 4;
				if( fx_count == 2 ) arpeggio_add = note_param & 0xF;
				break;
			case 0x19: /* Retrig.*/
				if( fx_count >= note_param ) {
					fx_count = 0;
					sample_idx = sample_fra = 0;
				}
				break;
			case 0x1C: /* Note Cut.*/
				if( note_param == fx_count ) volume = 0;
				break;
			case 0x1D: /* Note Delay.*/
				if( note_param == fx_count ) trigger();
				break;
		}
		if( note_effect > 0 ) update_frequency();
	}

	private void update_frequency() {
		int period, freq, volume;
		period = this.period + vibrato_add;
		if( period < 14 ) period = 14;
		freq = module.c2_rate * 107 / period;
		freq = ( freq * arp_tuning[ arpeggio_add ] >> 12 ) & 0xFFFF;
		step = ( freq << FP_SHIFT ) / ( sample_rate >> 2 );
		volume = this.volume + tremolo_add;
		if( volume > 64 ) volume = 64;
		if( volume < 0 ) volume = 0;
		volume = volume * FP_ONE >> 6;
		ampl = volume * module.gain >> 7;
	}

	private void trigger() {
		if( note_ins > 0 && note_ins <= module.num_instruments ) {
			assigned = note_ins;
			Instrument assigned_ins = module.instruments[ assigned ];
			fine_tune = assigned_ins.fine_tune & 0xF;
			volume = assigned_ins.volume >= 64 ? 64 : assigned_ins.volume & 0x3F;
			if( assigned_ins.loop_length > 0 && instrument > 0 ) instrument = assigned;
		}
		if( note_key > 0 ) {
			int key = ( note_key * fine_tuning[ fine_tune ] ) >> 11;
			porta_period = ( key >> 1 ) + ( key & 1 );
			if( note_effect != 0x3 && note_effect != 0x5 ) {
				instrument = assigned;
				period = porta_period;
				sample_idx = sample_fra = 0;
				if( vibrato_type < 4 ) vibrato_phase = 0;
				if( tremolo_type < 4 ) tremolo_phase = 0;
			}
		}
	}
	
	private void volume_slide( int param ) {
		int vol = volume + ( param >> 4 ) - ( param & 0xF );
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		volume = vol;
	}

	private void tone_portamento() {
		int source = period;
		int dest = porta_period;
		if( source < dest ) {
			source += porta_speed;
			if( source > dest ) source = dest;
		} else if( source > dest ) {
			source -= porta_speed;
			if( source < dest ) source = dest;
		}
		period = source;
	}

	private void vibrato() {
		vibrato_add = waveform( vibrato_phase, vibrato_type ) * vibrato_depth >> 7;
	}
	
	private void tremolo() {
		tremolo_add = waveform( tremolo_phase, tremolo_type ) * tremolo_depth >> 6;
	}

	private int waveform( int phase, int type ) {
		int amplitude = 0;
		switch( type & 0x3 ) {
			case 0: /* Sine. */
				amplitude = sine_table[ phase & 0x1F ];
				if( ( phase & 0x20 ) > 0 ) amplitude = -amplitude;
				break;
			case 1: /* Saw Down. */
				amplitude = 255 - ( ( ( phase + 0x20 ) & 0x3F ) << 3 );
				break;
			case 2: /* Square. */
				amplitude = ( phase & 0x20 ) > 0 ? 255 : -255;
				break;
			case 3: /* Random. */
				amplitude = random_seed - 255;
				random_seed = ( random_seed * 65 + 17 ) & 0x1FF;
				break;
		}
		return amplitude;
	}
}

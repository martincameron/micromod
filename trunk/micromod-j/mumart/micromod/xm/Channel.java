
package mumart.micromod.xm;

public class Channel {
	private static final int
		FP_SHIFT = 14,
		FP_ONE = 1 << FP_SHIFT,
		FP_MASK = FP_ONE - 1;

	private static final int[] period_table = {
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

	private static final int[] freq_table = {
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

	private static final short[] arp_tuning = {
		4096, 4340, 4598, 4871, 5161, 5468, 5793, 6137,
		6502, 6889, 7298, 7732, 8192, 8679, 9195, 9742
	};

	private static final short[] sine_table = {
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	};

	private Module module;
	private GlobalVol global_vol;
	private Instrument instrument;
	private Sample sample;
	private boolean key_on;
	private int note_key, note_ins, note_vol, note_effect, note_param;
	private int volume, panning, fine_tune;
	private int sample_idx, sample_fra, step, ampl, pann;
	private int fade_out_vol, vol_env_tick, pan_env_tick;
	private int period, porta_period, retrig_count, fx_count, auto_vibrato_count;
	private int porta_up_param, porta_down_param, tone_porta_param, offset_param;
	private int fine_porta_up_param, fine_porta_down_param, extra_fine_porta_param;
	private int vslide_param, global_vslide_param, panning_slide_param;
	private int fine_vslide_up_param, fine_vslide_down_param;
	private int retrig_volume, retrig_ticks, tremor_on_ticks, tremor_off_ticks;
	private int vibrato_type, vibrato_phase, vibrato_speed, vibrato_depth;
	private int tremolo_type, tremolo_phase, tremolo_speed, tremolo_depth;
	private int tremolo_add, vibrato_add, arpeggio_add;
	private int id, sample_rate, random_seed;
	public int pl_row;
	
	public Channel( Module module, int id, int sample_rate, GlobalVol global_vol ) {
		this.module = module;
		this.id = random_seed = id;
		this.sample_rate = sample_rate;
		this.global_vol = global_vol;
		instrument = new Instrument();
		sample = instrument.samples[ 0 ];
	}
	
	public void resample( int[] out_buf, int offset, int length, boolean interpolate ) {
		if( ampl <= 0 ) return;
		int l_ampl = ampl * ( 255 - pann ) >> 8;
		int r_ampl = ampl * pann >> 8;
		int sam_idx = sample_idx;
		int sam_fra = sample_fra;
		int step = this.step;
		int loop_len = sample.loop_length;
		int loop_ep1 = sample.loop_start + loop_len;
		short[] sample_data = sample.sample_data;
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
				int y = ( m * sam_fra >> FP_SHIFT ) + c;
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
				out_buf[ out_idx++ ] += y * l_ampl >> FP_SHIFT;
				out_buf[ out_idx++ ] += y * r_ampl >> FP_SHIFT;
				sam_fra += step;
				sam_idx += sam_fra >> FP_SHIFT;
				sam_fra &= FP_MASK;
			}
		}
	}

	public void update_sample_idx( int length ) {
		sample_fra += step * length;
		sample_idx += sample_fra >> FP_SHIFT;
		int loop_start = sample.loop_start;
		int loop_length = sample.loop_length;
		int loop_offset = sample_idx - loop_start;
		if( loop_offset > 0 ) {
			sample_idx = loop_start;
			if( loop_length > 1 ) sample_idx += loop_offset % loop_length;
		}
		sample_fra &= FP_MASK;
	}

	public void row( Note note ) {
		note_key = note.key;
		note_ins = note.instrument;
		note_vol = note.volume;
		note_effect = note.effect;
		note_param = note.param;
		retrig_count++;
		vibrato_add = tremolo_add = arpeggio_add = fx_count = 0;
		if( note_effect != 0x10D ) trigger();
		switch( note_effect ) {
			case 0x001: /* Porta Up. */
				if( note_param > 0 ) porta_up_param = note_param;
				break;
			case 0x002: /* Porta Down. */
				if( note_param > 0 ) porta_down_param = note_param;
				break;
			case 0x003: /* Tone Porta. */
				if( note_param > 0 ) tone_porta_param = note_param;
				break;
			case 0x004: /* Vibrato. */
				if( ( note_param >> 4 ) > 0 ) vibrato_speed = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) vibrato_depth = note_param & 0xF;
				vibrato();
				break;
			case 0x005: /* Tone Porta + Vol Slide. */
				if( note_param > 0 ) vslide_param = note_param;
				break;
			case 0x006: /* Vibrato + Vol Slide. */
				if( note_param > 0 ) vslide_param = note_param;
				vibrato();
				break;
			case 0x007: /* Tremolo. */
				if( ( note_param >> 4 ) > 0 ) tremolo_speed = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) tremolo_depth = note_param & 0xF;
				tremolo();
				break;
			case 0x008: /* Set Panning.*/
				panning = note_param & 0xFF;
				break;
			case 0x009: /* Set Sample Offset. */
				if( note_param > 0 ) offset_param = note_param;
				sample_idx = offset_param << 8;
				sample_fra = 0;
				break;
			case 0x00A: /* Vol Slide. */
				if( note_param > 0 ) vslide_param = note_param;
				break;
			case 0x00C: /* Set Volume. */
				volume = note_param >= 64 ? 64 : note_param & 0x3F;
				break;
			case 0x010: /* Set Global Volume. */
				global_vol.volume = note_param >= 64 ? 64 : note_param & 0x3F;
				break;
			case 0x011: /* Global Volume Slide. */
				if( note_param > 0 ) global_vslide_param = note_param;
				break;
			case 0x014: /* Key Off. */
				key_on = false;
				break;
			case 0x015: /* Set Envelope Tick. */
				vol_env_tick = pan_env_tick = note_param & 0xFF;
				break;
			case 0x019: /* Panning Slide. */
				if( note_param > 0 ) panning_slide_param = note_param;
				break;
			case 0x01B: /* Retrig + Vol Slide. */
				if( ( note_param >> 4 ) > 0 ) retrig_volume = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) retrig_ticks = note_param & 0xF;
				retrig_vol_slide();
				break;
			case 0x01D: /* Tremor. */
				if( ( note_param >> 4 ) > 0 ) tremor_on_ticks = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) tremor_off_ticks = note_param & 0xF;
				tremor();
				break;
			case 0x021: /* Extra Fine Porta. */
				if( note_param > 0 ) extra_fine_porta_param = note_param;
				switch( extra_fine_porta_param & 0xF0 ) {
					case 0x10: period -= extra_fine_porta_param & 0xF;
					case 0x20: period += extra_fine_porta_param & 0xF;
				}
				break;
			case 0x101: /* Fine Porta Up. */
				if( note_param > 0 ) fine_porta_up_param = note_param;
				period -= fine_porta_up_param << 2;
				break;
			case 0x102: /* Fine Porta Down. */
				if( note_param > 0 ) fine_porta_down_param = note_param;
				period += fine_porta_down_param << 2;
				break;
			case 0x104: /* Set Vibrato Waveform. */
				if( note_param < 8 ) vibrato_type = note_param;
				break;
			case 0x105: /* Set Finetune. */
				fine_tune = ( note_param & 0xF ) << 4;
				if( fine_tune > 127 ) fine_tune -= 256;
				break;
			case 0x107: /* Set Tremolo Waveform. */
				if( note_param < 8 ) tremolo_type = note_param;
				break;
			case 0x10A: /* Fine Vol Slide Up. */
				if( note_param > 0 ) fine_vslide_up_param = note_param;
				volume += fine_vslide_up_param;
				if( volume > 64 ) volume = 64;
				break;
			case 0x10B: /* Fine Vol Slide Down. */
				if( note_param > 0 ) fine_vslide_down_param = note_param;
				volume -= fine_vslide_down_param;
				if( volume < 0 ) volume = 0;
				break;
			case 0x10C: /* Note Cut. */
				if( note_param <= 0 ) volume = 0;
				break;
			case 0x10D: /* Note Delay. */
				if( note_param <= 0 ) trigger();
				break;
		}
		auto_vibrato();
		calculate_frequency();
		calculate_amplitude();
		update_envelopes();
	}
	
	public void tick() {
		vibrato_add = 0;
		fx_count++;
		retrig_count++;
		if( !( note_effect == 0x10D && fx_count <= note_param ) ) {
			switch( note_vol & 0xF0 ) {
				case 0x60: /* Vol Slide Down.*/
					volume -= note_vol & 0xF;
					if( volume < 0 ) volume = 0;
					break;
				case 0x70: /* Vol Slide Up.*/
					volume += note_vol & 0xF;
					if( volume > 64 ) volume = 64;
					break;
				case 0xB0: /* Vibrato.*/
					vibrato_phase += vibrato_speed;
					vibrato();
					break;
				case 0xD0: /* Pan Slide Left.*/
					panning -= note_vol & 0xF;
					if( panning < 0 ) panning = 0;
					break;
				case 0xE0: /* Pan Slide Right.*/
					panning += note_vol & 0xF;
					if( panning > 255 ) panning = 255;
					break;
				case 0xF0: /* Tone Porta.*/
					tone_portamento();
					break;
			}
		}
		switch( note_effect ) {
			case 0x001: /* Porta Up. */
				period -= porta_up_param << 2;
				if( period < 0 ) period = 0;
				break;
			case 0x002: /* Porta Down. */
				period += porta_down_param << 2;
				if( period > 65535 ) period = 65535;
				break;
			case 0x003: /* Tone Porta. */
				tone_portamento();
				break;
			case 0x004: /* Vibrato. */
				vibrato_phase += vibrato_speed;
				vibrato();
				break;
			case 0x005: /* Tone Porta + Vol Slide. */
				tone_portamento();
				volume_slide( vslide_param );
				break;
			case 0x006: /* Vibrato + Vol Slide. */
				vibrato_phase += vibrato_speed;
				vibrato();
				volume_slide( vslide_param );
				break;
			case 0x007: /* Tremolo. */
				tremolo_phase += tremolo_speed;
				tremolo();
				break;
			case 0x00A: /* Vol Slide. */
				volume_slide( vslide_param );
				break;
			case 0x00E: /* Arpeggio. */
				if( fx_count > 2 ) fx_count = 0;
				if( fx_count == 0 ) arpeggio_add = 0;
				if( fx_count == 1 ) arpeggio_add = note_param >> 4;
				if( fx_count == 2 ) arpeggio_add = note_param & 0xF;
				break;
			case 0x011: /* Global Volume Slide. */
				global_vol.volume += ( global_vslide_param >> 4 ) - ( global_vslide_param & 0xF );
				if( global_vol.volume < 0 ) global_vol.volume = 0;
				if( global_vol.volume > 64 ) global_vol.volume = 64;
				break;
			case 0x019: /* Panning Slide. */
				panning += ( panning_slide_param >> 4 ) - ( panning_slide_param & 0xF );
				if( panning < 0 ) panning = 0;
				if( panning > 255 ) panning = 255;
				break;
			case 0x01B: /* Retrig + Vol Slide. */
				retrig_vol_slide();
				break;
			case 0x01D: /* Tremor. */
				tremor();
				break;
			case 0x109: /* Retrig. */
				if( fx_count >= note_param ) {
					fx_count = 0;
					sample_idx = sample_fra = 0;
				}
				break;
			case 0x10C: /* Note Cut. */
				if( note_param == fx_count ) volume = 0;
				break;
			case 0x10D: /* Note Delay. */
				if( note_param == fx_count ) trigger();
				break;
		}
		auto_vibrato();
		calculate_frequency();
		calculate_amplitude();
		update_envelopes();
	}

	private void update_envelopes() {
		if( instrument.volume_envelope.enabled ) {
			if( !key_on ) {
				fade_out_vol -= instrument.volume_fade_out;
				if( fade_out_vol < 0 ) fade_out_vol = 0;
			}
			vol_env_tick = instrument.volume_envelope.next_tick( vol_env_tick, key_on );
		}
		if( instrument.panning_envelope.enabled )
			pan_env_tick = instrument.panning_envelope.next_tick( pan_env_tick, key_on );
	}

	private void auto_vibrato() {
		int depth = instrument.vibrato_depth & 0x7F;
		if( depth > 0 ) {
			int sweep = instrument.vibrato_sweep & 0x7F;
			int rate = instrument.vibrato_rate & 0x7F;
			int type = instrument.vibrato_type;
			if( auto_vibrato_count < sweep ) depth = depth * auto_vibrato_count / sweep;
			vibrato_add += waveform( auto_vibrato_count * rate >> 2, type + 4 ) * depth >> 8;
			auto_vibrato_count++;
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
			source += tone_porta_param << 2;
			if( source > dest ) source = dest;
		} else if( source > dest ) {
			source -= tone_porta_param << 2;
			if( source < dest ) source = dest;
		}
		period = source;
	}

	private void vibrato() {
		vibrato_add += waveform( vibrato_phase, vibrato_type & 0x3 ) * vibrato_depth >> 5;
	}

	private void tremolo() {
		tremolo_add = waveform( tremolo_phase, tremolo_type & 0x3 ) * tremolo_depth >> 6;
	}

	private int waveform( int phase, int type ) {
		int amplitude = 0;
		switch( type ) {
			default: /* Sine. */
				amplitude = sine_table[ phase & 0x1F ];
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
				amplitude = random_seed - 255;
				random_seed = ( random_seed * 65 + 17 ) & 0x1FF;
				break;
		}
		return amplitude;
	}

	private void tremor() {
		if( retrig_count >= tremor_on_ticks ) tremolo_add = -64;
		if( retrig_count >= ( tremor_on_ticks + tremor_off_ticks ) )
			tremolo_add = retrig_count = 0;
	}

	private void retrig_vol_slide() {
		if( retrig_count >= retrig_ticks ) {
			retrig_count = sample_idx = sample_fra = 0;
			switch( retrig_volume ) {
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

	private void calculate_frequency() {
		if( module.linear_periods ) {
			int per = period + vibrato_add - ( arpeggio_add << 6 );
			if( per < 28 ) per = 28;
			if( per > 7680 ) per = 7680;
			int tone = 7680 - per;
			int i = ( tone >> 3 ) % 96;
			int c = freq_table[ i ];
			int m = freq_table[ i + 1 ] - c;
			int x = tone & 0x7;
			int y = ( ( m * x ) >> 3 ) + c;
			int freq = y >> ( 9 - tone / 768 );
			step = ( freq << ( FP_SHIFT - 2 ) ) / ( sample_rate >> 2 );
		} else {
			int per = period + vibrato_add;
			if( per < 28 ) per = 28;
			int freq = 8363 * 428 / per;
			freq = freq * arp_tuning[ arpeggio_add ] >> 12;
			step = ( freq << FP_SHIFT ) / ( sample_rate >> 2 );
		}
	}

	private void calculate_amplitude() {
		int env_vol = key_on ? 64 : 0;
		if( instrument.volume_envelope.enabled )
			env_vol = instrument.volume_envelope.calculate_ampl( vol_env_tick );
		int vol = volume + tremolo_add;
		if( vol > 64 ) vol = 64;
		if( vol < 0 ) vol = 0;
		vol = vol * FP_ONE >> 7;
		vol = vol * fade_out_vol >> 15;
		ampl = vol * global_vol.volume * env_vol >> 12;
		int env_pan = 32;
		if( instrument.panning_envelope.enabled )
			env_pan = instrument.panning_envelope.calculate_ampl( pan_env_tick );
		int pan_range = ( panning < 128 ) ? panning : ( 255 - panning );
		pann = panning + ( pan_range * ( env_pan - 32 ) >> 5 );
	}
	
	private void trigger() {
		if( note_ins > 0 && note_ins <= module.num_instruments ) {
			instrument = module.instruments[ note_ins ];
			sample = instrument.samples[ instrument.key_to_sample[ note_key < 97 ? note_key : 0 ] ];
			volume = sample.volume >= 64 ? 64 : sample.volume & 0x3F;
			panning = sample.panning & 0xFF;
			fine_tune = ( byte ) sample.fine_tune;
			vol_env_tick = pan_env_tick = 0;
			fade_out_vol = 32768;
			key_on = true;
		}
		if( note_vol >= 0x10 && note_vol < 0x60 )
			volume = note_vol < 0x50 ? note_vol - 0x10 : 64;
		switch( note_vol & 0xF0 ) {
			case 0x80: /* Fine Vol Down.*/
				volume -= note_vol & 0xF;
				if( volume < 0 ) volume = 0;
				break;
			case 0x90: /* Fine Vol Up.*/
				volume += note_vol & 0xF;
				if( volume > 64 ) volume = 64;
				break;
			case 0xA0: /* Set Vibrato Speed.*/
				if( ( note_vol & 0xF ) > 0 ) vibrato_speed = note_vol & 0xF;
				break;
			case 0xB0: /* Vibrato.*/
				if( ( note_vol & 0xF ) > 0 ) vibrato_depth = note_vol & 0xF;
				vibrato();
				break;
			case 0xC0: /* Set Panning.*/
				panning = ( note_vol & 0xF ) * 17;
				break;
			case 0xF0: /* Tone Porta.*/
				if( ( note_vol & 0xF ) > 0 ) tone_porta_param = note_vol & 0xF;
				break;
		}
		if( note_key > 0 ) {
			if( note_key > 96 ) {
				key_on = false;
			} else {
				int key = note_key + sample.rel_note;
				if( key < 1 ) key = 1;
				if( key > 120 ) key = 120;
				if( module.linear_periods ) {
					porta_period = 7680 - ( ( key - 1 ) << 6 ) - ( fine_tune >> 1 );
				} else {
					int tone = 768 + ( ( key - 1 ) << 6 ) + ( fine_tune >> 1 );
					int i = ( tone >> 3 ) % 96;
					int c = period_table[ i ];
					int m = period_table[ i + 1 ] - c;
					int x = tone & 0x7;
					int y = ( ( m * x ) >> 3 ) + c;
					porta_period = y >> ( tone / 768 );
				}
				if( note_effect != 0x03 && note_effect != 0x05 && ( note_vol & 0xF0 ) != 0xF0 ) {
					this.period = porta_period;
					sample_idx = sample_fra = 0;
					if( vibrato_type < 4 ) vibrato_phase = 0;
					if( tremolo_type < 4 ) tremolo_phase = 0;
					retrig_count = auto_vibrato_count = 0;
				}
			}
		}
	}
}

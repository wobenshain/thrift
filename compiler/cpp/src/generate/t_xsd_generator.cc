/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#include <fstream>
#include <iostream>
#include <sstream>

#include <stdlib.h>
#include <sys/stat.h>
#include <sstream>
#include "t_generator.h"
#include "platform.h"
using std::map;
using std::ofstream;
using std::ostream;
using std::ostringstream;
using std::string;
using std::stringstream;
using std::vector;

static const string endl = "\n";  // avoid ostream << std::endl flushes


/**
 * XSD generator, creates an XSD for the base types etc.
 *
 */
class t_xsd_generator : public t_generator {
 public:
  t_xsd_generator(
      t_program* program,
      const std::map<std::string, std::string>& parsed_options,
      const std::string& option_string)
    : t_generator(program)
  {
    (void) parsed_options;
    (void) option_string;
    out_dir_base_ = "gen-xsd";
  }

  virtual ~t_xsd_generator() {}

  /**
   * Init and close methods
   */

  void init_generator();
  void close_generator();

  /**
   * Program-level generation functions
   */

  void generate_typedef(t_typedef* ttypedef);
  void generate_enum(t_enum* tenum);

  void generate_service(t_service* tservice);
  void generate_struct(t_struct* tstruct);

 private:

  void generate_element(std::ostream& out, std::string name, t_type* ttype, t_struct* attrs=NULL, bool optional=false, bool nillable=false, bool list_element=false);

  std::string ns(std::string in, std::string ns) {
    return ns + ":" + in;
  }

  std::string xsd(std::string in) {
    return ns(in, "xsd");
  }

  std::string type_name(t_type* ttype);
  std::string base_type_name(t_base_type::t_base tbase);

  void generate_header(std::ostream& out);
  void generate_footer(std::ostream& out);

  /**
   * Output xsd/php file
   */
  std::ofstream f_xsd_;

  /**
   * Output string stream
   */
  std::ostringstream s_xsd_types_;
};


void t_xsd_generator::init_generator() {
  // Make output directory
  MKDIR(get_out_dir().c_str());
}

void t_xsd_generator::close_generator() {
}

void t_xsd_generator::generate_typedef(t_typedef* ttypedef) {
  string f_xsd_name = get_out_dir()+ttypedef->get_symbolic()+".xsd";
  std::ofstream f_outstream;
  std::ostringstream s_temporary;
  f_outstream.open(f_xsd_name.c_str());

  generate_header(f_outstream);

  indent(s_temporary) <<
    "<xsd:simpleType name=\"" << ttypedef->get_name() << "\">" << endl;
  indent_up();
  if (ttypedef->get_type()->is_string() && ((t_base_type*)ttypedef->get_type())->is_string_enum()) {
    indent(s_temporary) <<
      "<xsd:restriction base=\"" << type_name(ttypedef->get_type()) << "\">" << endl;
    indent_up();
    const vector<string>& values = ((t_base_type*)ttypedef->get_type())->get_string_enum_vals();
    vector<string>::const_iterator v_iter;
    for (v_iter = values.begin(); v_iter != values.end(); ++v_iter) {
      indent(s_temporary) <<
        "<xsd:enumeration value=\"" << (*v_iter) << "\" />" << endl;
    }
    indent_down();
    indent(s_temporary) <<
      "</xsd:restriction>" << endl;
  } else {
    indent(s_temporary) <<
      "<xsd:restriction base=\"" << type_name(ttypedef->get_type()) << "\" />" << endl;
  }
  indent_down();
  indent(s_temporary) <<
    "</xsd:simpleType>" << endl << endl;

  s_xsd_types_ << s_temporary.str();
  f_outstream << s_temporary.str();

  generate_footer(f_outstream);
  f_outstream.close();
}

void t_xsd_generator::generate_enum(t_enum* tenum) {
  string f_xsd_name = get_out_dir()+tenum->get_name()+".xsd";
  std::ofstream f_outstream;
  std::ostringstream s_temporary;
  f_outstream.open(f_xsd_name.c_str());

  generate_header(f_outstream);

  indent(s_temporary) << "<xs:simpleType name=\"" << tenum->get_name() << "\">" << endl;
  indent_up();
  indent(s_temporary) << "<xs:restriction base=\"xs:string\">" << endl;
  indent_up();
  vector<t_enum_value*> constants = tenum->get_constants();
  vector<t_enum_value*>::iterator c_iter;
  for (c_iter = constants.begin(); c_iter != constants.end(); ++c_iter) {
    indent(s_temporary) << "<xs:enumeration value=\"" << (*c_iter)->get_value() << "," << (*c_iter)->get_name() << "\" />" << endl;;
  }
  indent_down();
  indent(s_temporary) << "</xs:restriction>" << endl;
  indent_down();
  indent(s_temporary) << "</xs:simpleType>" << endl;

  s_xsd_types_ << s_temporary.str();
  f_outstream << s_temporary.str();

  generate_footer(f_outstream);
  f_outstream.close();
}

void t_xsd_generator::generate_struct(t_struct* tstruct) {
  string f_xsd_name = get_out_dir()+tstruct->get_name()+".xsd";
  std::ofstream f_outstream;
  std::ostringstream s_temporary;
  f_outstream.open(f_xsd_name.c_str());

  generate_header(f_outstream);

  vector<t_field*>::const_iterator m_iter;
  const vector<t_field*>& members = tstruct->get_members();
  bool xsd_all = tstruct->get_xsd_all();

  indent(s_temporary) << "<xsd:complexType name=\"" << tstruct->get_name() << "\">" << endl;
  indent_up();
  if (xsd_all) {
    indent(s_temporary) << "<xsd:all>" << endl;
  } else {
    indent(s_temporary) << "<xsd:sequence>" << endl;
  }
  indent_up();

  for (m_iter = members.begin(); m_iter != members.end(); ++m_iter) {
    generate_element(s_temporary, (*m_iter)->get_name(), (*m_iter)->get_type(), (*m_iter)->get_xsd_attrs(), (*m_iter)->get_xsd_optional() || xsd_all, (*m_iter)->get_xsd_nillable());
  }

  indent_down();
  if (xsd_all) {
    indent(s_temporary) << "</xsd:all>" << endl;
  } else {
    indent(s_temporary) << "</xsd:sequence>" << endl;
  }
  indent_down();
  indent(s_temporary) <<
    "</xsd:complexType>" << endl <<
    endl;

  s_xsd_types_ << s_temporary.str();
  f_outstream << s_temporary.str();

  generate_footer(f_outstream);
  f_outstream.close();
}

void t_xsd_generator::generate_element(ostream& out,
                                       string name,
                                       t_type* ttype,
                                       t_struct* attrs,
                                       bool optional,
                                       bool nillable,
                                       bool list_element) {
  string sminOccurs = (optional || list_element) ? " minOccurs=\"0\"" : "";
  string smaxOccurs = list_element ? " maxOccurs=\"unbounded\"" : "";
  string soptional = sminOccurs + smaxOccurs;
  string snillable = nillable ? " nillable=\"true\"" : "";

  if (ttype->is_void() || ttype->is_list()) {
    indent(out) <<
      "<xsd:element name=\"" << name << "\"" << soptional << snillable << ">" << endl;
    indent_up();
    if (attrs == NULL && ttype->is_void()) {
      indent(out) <<
        "<xsd:complexType />" << endl;
    } else {
      indent(out) <<
        "<xsd:complexType>" << endl;
      indent_up();
      if (ttype->is_list()) {
        indent(out) << "<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">" << endl;
        indent_up();
        string subname;
        t_type* subtype = ((t_list*)ttype)->get_elem_type();
        if (subtype->is_base_type() || subtype->is_container()) {
          subname = name + "_elt";
        } else {
          subname = type_name(subtype);
        }
        generate_element(out, subname, subtype, NULL, false, false, true);
        indent_down();
        indent(out) << "</xsd:sequence>" << endl;
        indent(out) << "<xsd:attribute name=\"list\" type=\"xsd:boolean\" />" << endl;
      }
      if (attrs != NULL) {
        const vector<t_field*>& members = attrs->get_members();
        vector<t_field*>::const_iterator a_iter;
        for (a_iter = members.begin(); a_iter != members.end(); ++a_iter) {
          indent(out) << "<xsd:attribute name=\"" << (*a_iter)->get_name() << "\" type=\"" << type_name((*a_iter)->get_type()) << "\" />" << endl;
        }
      }
      indent_down();
      indent(out) <<
        "</xsd:complexType>" << endl;
    }
    indent_down();
    indent(out) <<
      "</xsd:element>" << endl;
  } else {
    if (attrs == NULL) {
      indent(out) <<
        "<xsd:element name=\"" << name << "\"" << " type=\"" << type_name(ttype) << "\"" << soptional << snillable << " />" << endl;
    } else {
      // Wow, all this work for a SIMPLE TYPE with attributes?!?!?!
      indent(out) << "<xsd:element name=\"" << name << "\"" << soptional << snillable << ">" << endl;
      indent_up();
      indent(out) << "<xsd:complexType>" << endl;
      indent_up();
      indent(out) << "<xsd:complexContent>" << endl;
      indent_up();
      indent(out) << "<xsd:extension base=\"" << type_name(ttype) << "\">" << endl;
      indent_up();
      const vector<t_field*>& members = attrs->get_members();
      vector<t_field*>::const_iterator a_iter;
      for (a_iter = members.begin(); a_iter != members.end(); ++a_iter) {
        indent(out) << "<xsd:attribute name=\"" << (*a_iter)->get_name() << "\" type=\"" << type_name((*a_iter)->get_type()) << "\" />" << endl;
      }
      indent_down();
      indent(out) << "</xsd:extension>" << endl;
      indent_down();
      indent(out) << "</xsd:complexContent>" << endl;
      indent_down();
      indent(out) << "</xsd:complexType>" << endl;
      indent_down();
      indent(out) << "</xsd:element>" << endl;
    }
  }
}

void t_xsd_generator::generate_service(t_service* tservice) {
  // Make output file
  string f_xsd_name = get_out_dir()+tservice->get_name()+".xsd";
  f_xsd_.open(f_xsd_name.c_str());

  string ns = program_->get_namespace("xsd");
  if (ns.size() > 0) {
    ns = " targetNamespace=\"" + ns + "\" xmlns=\"" + ns + "\" " +
      "elementFormDefault=\"qualified\"";
  }

  // Print the XSD header
  generate_header(f_xsd_);

  // Print out the type definitions
  indent(f_xsd_) << s_xsd_types_.str();

  // Keep a list of all the possible exceptions that might get thrown
  map<string, t_struct*> all_xceptions;

  // List the elements that you might actually get
  vector<t_function*> functions = tservice->get_functions();
  vector<t_function*>::iterator f_iter;
  for (f_iter = functions.begin(); f_iter != functions.end(); ++f_iter) {
    string elemname = (*f_iter)->get_name() + "_response";
    t_type* returntype = (*f_iter)->get_returntype();
    generate_element(f_xsd_, elemname, returntype);
    f_xsd_ << endl;

    t_struct* xs = (*f_iter)->get_xceptions();
    const std::vector<t_field*>& xceptions = xs->get_members();
    vector<t_field*>::const_iterator x_iter;
    for (x_iter = xceptions.begin(); x_iter != xceptions.end(); ++x_iter) {
      all_xceptions[(*x_iter)->get_name()] = (t_struct*)((*x_iter)->get_type());
    }
  }

  map<string, t_struct*>::iterator ax_iter;
  for (ax_iter = all_xceptions.begin(); ax_iter != all_xceptions.end(); ++ax_iter) {
    generate_element(f_xsd_, ax_iter->first, ax_iter->second);
  }

  // Close the XSD document
  generate_footer(f_xsd_);
  f_xsd_.close();
}

string t_xsd_generator::type_name(t_type* ttype) {
  if (ttype->is_typedef()) {
    return ttype->get_name();
  }

  if (ttype->is_base_type()) {
    return xsd(base_type_name(((t_base_type*)ttype)->get_base()));
  }

  if (ttype->is_enum()) {
    return xsd("int");
  }

  if (ttype->is_struct() || ttype->is_xception()) {
    return ttype->get_name();
  }

  return "container";
}

/**
 * Returns the XSD type that corresponds to the thrift type.
 *
 * @param tbase The base type
 * @return Explicit XSD type, i.e. xsd:string
 */
string t_xsd_generator::base_type_name(t_base_type::t_base tbase) {
  switch (tbase) {
  case t_base_type::TYPE_VOID:
    return "void";
  case t_base_type::TYPE_STRING:
    return "string";
  case t_base_type::TYPE_BOOL:
    return "boolean";
  case t_base_type::TYPE_BYTE:
    return "byte";
  case t_base_type::TYPE_I16:
    return "short";
  case t_base_type::TYPE_I32:
    return "int";
  case t_base_type::TYPE_I64:
    return "long";
  case t_base_type::TYPE_DOUBLE:
    return "decimal";
  default:
    throw "compiler error: no XSD base type name for base type " + t_base_type::t_base_name(tbase);
  }
}

void t_xsd_generator::generate_header(std::ostream& out) {
  string ns = program_->get_namespace("xsd");
  if (ns.size() > 0) {
    ns = " targetNamespace=\"" + ns + "\" xmlns=\"" + ns + "\" " +
      "elementFormDefault=\"qualified\"";
  }

  // Print the XSD header
  out <<
    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" << endl <<
    "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"" << ns << ">" << endl <<
    endl;
  indent_up();
  indent(out) << "<!-- This XSD was generated by Thrift. -->" << endl << endl;
}

void t_xsd_generator::generate_footer(std::ostream& out) {
  indent_down();
  out << "</xsd:schema>" << endl;
}

THRIFT_REGISTER_GENERATOR(xsd, "XSD", "")

